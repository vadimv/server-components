package rsp.http;

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
    private final Object lock = new Object();
    private final Object schedulerLock = new Object();
    private final Map<QualifiedSessionId, RenderedPage> renderedPages;
    private final Supplier<EventLoop> eventLoopSupplier;
    private final LocalSessionResumeConfig config;
    private final Map<QualifiedSessionId, ResumablePageSession> liveSessions = new HashMap<>();

    private ScheduledExecutorService expiryExecutor;
    private boolean accepting = true;

    LocalSessionRegistry(final Map<QualifiedSessionId, RenderedPage> renderedPages,
                         final Supplier<EventLoop> eventLoopSupplier,
                         final LocalSessionResumeConfig config) {
        this.renderedPages = Objects.requireNonNull(renderedPages);
        this.eventLoopSupplier = Objects.requireNonNull(eventLoopSupplier);
        this.config = Objects.requireNonNull(config);
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
            final ResumablePageSession created = new ResumablePageSession(sessionId,
                                                                          renderedPage,
                                                                          eventLoopSupplier.get(),
                                                                          config,
                                                                          this::schedule,
                                                                          closed -> remove(sessionId, closed));
            liveSessions.put(sessionId, created);
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
        }
    }

    void closeAll() {
        final ArrayList<ResumablePageSession> sessions;
        final ScheduledExecutorService executor;
        synchronized (lock) {
            accepting = false;
            sessions = new ArrayList<>(liveSessions.values());
        }
        sessions.forEach(session -> session.close("server-stopping"));
        synchronized (schedulerLock) {
            executor = expiryExecutor;
            expiryExecutor = null;
        }
        if (executor != null) {
            executor.shutdownNow();
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

    private void remove(final QualifiedSessionId sessionId, final ResumablePageSession session) {
        synchronized (lock) {
            liveSessions.remove(sessionId, session);
        }
    }
}
