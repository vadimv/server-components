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

    /** Takes ownership of a successfully rendered live page and starts its event loop. */
    void register(final QualifiedSessionId sessionId, final RenderedPage page) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(page);
        final ResumablePageSession created;
        try {
            created = new ResumablePageSession(sessionId, page, eventLoopSupplier.get(),
                    config, this::schedule,
                    attached -> attached(sessionId, attached),
                    closed -> remove(sessionId, closed));
        } catch (RuntimeException | Error failure) {
            closePending(page);
            throw failure;
        }

        try {
            final boolean accepted;
            synchronized (lock) {
                accepted = accepting;
                if (accepted) {
                    if (liveSessions.containsKey(sessionId)
                            || renderedPages.putIfAbsent(sessionId, page) != null) {
                        throw new IllegalStateException("Duplicate page session: " + sessionId);
                    }
                    liveSessions.put(sessionId, created);
                    updateSessionGauge();
                }
            }
            // Publish ownership before startup can execute callbacks. Closing may
            // win this race; a closed session's start is a no-op.
            if (accepted) {
                created.start();
            } else {
                created.close("server-stopping");
            }
        } catch (RuntimeException | Error failure) {
            try {
                created.close("startup-failed");
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    Optional<ResumablePageSession> find(final QualifiedSessionId sessionId) {
        Objects.requireNonNull(sessionId);
        final ResumablePageSession session;
        synchronized (lock) {
            session = accepting ? liveSessions.get(sessionId) : null;
        }
        // Never acquire the session monitor under the registry lock: startup
        // and transport callbacks can remove a session from this registry.
        return session == null || session.isClosed() ? Optional.empty() : Optional.of(session);
    }

    private void attached(final QualifiedSessionId sessionId, final ResumablePageSession session) {
        synchronized (lock) {
            if (liveSessions.get(sessionId) == session) {
                renderedPages.remove(sessionId);
            }
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
        final ScheduledExecutorService executor;
        synchronized (lock) {
            accepting = false;
            sessions = new ArrayList<>(liveSessions.values());
            renderedPages.clear();
        }
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
                renderedPages.remove(sessionId);
                updateSessionGauge();
            }
        }
    }

    /** Must be called while holding {@link #lock}. */
    private void updateSessionGauge() {
        metrics.setGauge(MetricNames.PAGE_SESSIONS_ACTIVE, liveSessions.size());
    }
}
