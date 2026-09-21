package rsp.http;

import rsp.metrics.MetricNames;
import rsp.metrics.Metrics;
import rsp.page.EventLoop;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderedPage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Process-local ownership and expiry of resumable live pages.
 */
final class LocalSessionRegistry {
    private static final System.Logger logger = System.getLogger(LocalSessionRegistry.class.getName());
    private final Object lock = new Object();
    private final Object schedulerLock = new Object();
    private final Map<QualifiedSessionId, RenderedPage> renderedPages;
    private final Supplier<EventLoop> eventLoopSupplier;
    private final LocalSessionResumeConfig config;
    private final Metrics metrics;
    private final Map<QualifiedSessionId, ResumablePageSession> liveSessions = new HashMap<>();
    private final Map<QualifiedSessionId, ResumablePageSession.ExpiryTask> pendingExpiry = new HashMap<>();

    private ScheduledExecutorService expiryExecutor;
    private boolean accepting = true;

    LocalSessionRegistry(final Map<QualifiedSessionId, RenderedPage> renderedPages,
                         final Supplier<EventLoop> eventLoopSupplier,
                         final LocalSessionResumeConfig config) {
        this(renderedPages, eventLoopSupplier, config, Metrics.noop());
    }

    LocalSessionRegistry(final Map<QualifiedSessionId, RenderedPage> renderedPages,
                         final Supplier<EventLoop> eventLoopSupplier,
                         final LocalSessionResumeConfig config,
                         final Metrics metrics) {
        this.renderedPages = Objects.requireNonNull(renderedPages);
        this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier);
        this.config = Objects.requireNonNull(config);
        this.metrics = Objects.requireNonNull(metrics);
    }

    /** Retains a rendered page only for the bounded initial WebSocket connection window. */
    void register(final QualifiedSessionId sessionId, final RenderedPage page) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(page);
        boolean accepted;
        synchronized (lock) {
            accepted = accepting;
            if (accepted) {
                RenderedPage previous = renderedPages.putIfAbsent(sessionId, page);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate page session: " + sessionId);
                }
                try {
                    pendingExpiry.put(sessionId, schedule(() -> expirePending(sessionId, page),
                            config.gracePeriod()));
                } catch (RuntimeException failure) {
                    renderedPages.remove(sessionId, page);
                    throw failure;
                }
            }
        }
        if (!accepted) {
            page.close();
        }
    }

    Optional<ResumablePageSession> findOrCreate(final QualifiedSessionId sessionId) {
        Objects.requireNonNull(sessionId);
        synchronized (lock) {
            if (!accepting) {
                return Optional.empty();
            }
            final ResumablePageSession existing = liveSessions.get(sessionId);
            if (existing != null && !existing.isClosed()) {
                return Optional.of(existing);
            }

            final RenderedPage renderedPage = renderedPages.remove(sessionId);
            if (renderedPage == null) {
                return Optional.empty();
            }
            final ResumablePageSession.ExpiryTask pendingTask = pendingExpiry.remove(sessionId);
            if (pendingTask != null) {
                pendingTask.cancel();
            }
            final ResumablePageSession created;
            try {
                created = new ResumablePageSession(sessionId,
                        renderedPage, eventLoopSupplier.get(), config, this::schedule,
                        closed -> remove(sessionId, closed));
            } catch (RuntimeException | Error failure) {
                closePending(renderedPage);
                throw failure;
            }
            liveSessions.put(sessionId, created);
            updateSessionGauge();
            return Optional.of(created);
        }
    }

    int size() {
        synchronized (lock) {
            return liveSessions.size();
        }
    }

    void start() {
        synchronized (lock) {
            accepting = true;
            updateSessionGauge();
        }
    }

    void closeAll() {
        final ArrayList<ResumablePageSession> sessions;
        final ArrayList<RenderedPage> pending;
        final ScheduledExecutorService executor;
        synchronized (lock) {
            accepting = false;
            sessions = new ArrayList<>(liveSessions.values());
            pending = new ArrayList<>(renderedPages.values());
            renderedPages.clear();
            pendingExpiry.values().forEach(ResumablePageSession.ExpiryTask::cancel);
            pendingExpiry.clear();
        }
        pending.forEach(LocalSessionRegistry::closePending);
        sessions.forEach(session -> session.close("server-stopping"));
        synchronized (schedulerLock) {
            executor = expiryExecutor;
            expiryExecutor = null;
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        synchronized (lock) {
            updateSessionGauge();
        }
    }

    private ResumablePageSession.ExpiryTask schedule(final Runnable task, final Duration delay) {
        synchronized (schedulerLock) {
            if (expiryExecutor == null || expiryExecutor.isShutdown()) {
                expiryExecutor = Executors.newSingleThreadScheduledExecutor(runnable ->
                        Thread.ofPlatform().daemon().name("rsp-local-session-expiry").unstarted(runnable));
            }
            final var future = expiryExecutor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        }
    }

    private void expirePending(final QualifiedSessionId sessionId, final RenderedPage page) {
        final boolean removed;
        synchronized (lock) {
            removed = renderedPages.remove(sessionId, page);
            if (removed) {
                pendingExpiry.remove(sessionId);
            }
        }
        if (removed) {
            closePending(page);
        }
    }

    private static void closePending(final RenderedPage page) {
        try {
            page.close();
        } catch (RuntimeException | Error failure) {
            logger.log(System.Logger.Level.WARNING, "Pending page cleanup failed", failure);
        }
    }

    private void remove(final QualifiedSessionId sessionId, final ResumablePageSession session) {
        synchronized (lock) {
            if (liveSessions.remove(sessionId, session)) {
                updateSessionGauge();
            }
        }
    }

    /** Must be called while holding {@link #lock}. */
    private void updateSessionGauge() {
        metrics.setGauge(MetricNames.PAGE_SESSIONS_ACTIVE, liveSessions.size());
    }
}
