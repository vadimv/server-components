package rsp.http;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.page.DefaultEventLoop;
import rsp.page.EventLoop;
import rsp.page.events.GenericTaskEvent;
import rsp.page.PageBuilder;
import rsp.page.PageScope;
import rsp.page.QualifiedSessionId;
import rsp.page.RedirectableEventsConsumer;
import rsp.page.RenderedPage;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LocalSessionRegistryTests {
    private static final QualifiedSessionId ID = new QualifiedSessionId("device", "page");

    @Test
    void unconnectedRenderedPageExpiresAndClosesItsScope() throws Exception {
        Map<QualifiedSessionId, RenderedPage> pending = new ConcurrentHashMap<>();
        LocalSessionRegistry registry = registry(pending, Duration.ofMillis(30));
        CountDownLatch closed = new CountDownLatch(1);
        registry.register(ID, page(closed));

        assertTrue(closed.await(2, TimeUnit.SECONDS));
        assertTrue(pending.isEmpty());
        assertTrue(registry.find(ID).isEmpty());
        registry.closeAll();
    }

    @Test
    void serverStopClosesPendingPagesImmediately() throws Exception {
        Map<QualifiedSessionId, RenderedPage> pending = new ConcurrentHashMap<>();
        LocalSessionRegistry registry = registry(pending, Duration.ofMinutes(1));
        CountDownLatch closed = new CountDownLatch(1);
        registry.register(ID, page(closed));

        registry.closeAll();

        assertTrue(closed.await(1, TimeUnit.SECONDS));
        assertTrue(pending.isEmpty());
    }

    @Test
    void lookupReusesRunningSessionWithoutExtendingItsLifetime() throws Exception {
        Map<QualifiedSessionId, RenderedPage> pending = new ConcurrentHashMap<>();
        LocalSessionRegistry registry = registry(pending, Duration.ofMillis(150));
        CountDownLatch closed = new CountDownLatch(1);
        registry.register(ID, page(closed));

        var live = registry.find(ID).orElseThrow();
        assertFalse(closed.await(50, TimeUnit.MILLISECONDS));
        live.close("test");
        assertTrue(closed.await(2, TimeUnit.SECONDS));
        registry.closeAll();
    }

    @Test
    void registrationStartsQueuedWorkBeforeAnyWebSocketLookup() throws Exception {
        Map<QualifiedSessionId, RenderedPage> pending = new ConcurrentHashMap<>();
        LocalSessionRegistry registry = registry(pending, Duration.ofMinutes(1));
        CountDownLatch processed = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        RenderedPage page = page(closed);
        page.commandsEnqueue().offer(new GenericTaskEvent(processed::countDown));
        try {
            assertEquals(1, processed.getCount());
            registry.register(ID, page);
            assertTrue(processed.await(2, TimeUnit.SECONDS));
            assertEquals(1, registry.size());
            assertSame(page, pending.get(ID));
            assertSame(registry.find(ID).orElseThrow(), registry.find(ID).orElseThrow());
        } finally {
            registry.closeAll();
        }
        assertTrue(closed.await(2, TimeUnit.SECONDS));
    }

    @Test
    void startupFailureRemovesPublishedSessionAndClosesResourcesOnce() {
        Map<QualifiedSessionId, RenderedPage> pending = new ConcurrentHashMap<>();
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger stopped = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("start failed");
        EventLoop loop = new EventLoop() {
            public void start(Runnable logic) { throw failure; }
            public void stop() { stopped.incrementAndGet(); }
        };
        LocalSessionRegistry registry = new LocalSessionRegistry(pending, () -> loop,
                LocalSessionResumeConfig.defaults());
        RenderedPage page = page(new CountDownLatch(1));
        page.scope().own(closed::incrementAndGet);
        try {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> registry.register(ID, page)));
            assertTrue(registry.find(ID).isEmpty());
            assertTrue(pending.isEmpty());
            assertEquals(0, registry.size());
            assertEquals(1, stopped.get());
        } finally {
            registry.closeAll();
        }
        assertEquals(1, closed.get());
    }

    @Test
    void eventLoopFactoryFailureClosesTheRenderedPage() {
        LocalSessionRegistry registry = new LocalSessionRegistry(new ConcurrentHashMap<>(),
                () -> { throw new IllegalStateException("factory failed"); },
                LocalSessionResumeConfig.defaults());
        AtomicInteger closed = new AtomicInteger();
        RenderedPage page = page(new CountDownLatch(1));
        page.scope().own(closed::incrementAndGet);
        try {
            assertThrows(IllegalStateException.class, () -> registry.register(ID, page));
            assertEquals(0, registry.size());
            assertEquals(1, closed.get());
        } finally {
            registry.closeAll();
        }
    }

    @Test
    void shutdownDuringConstructionPreventsStartingTheNewPage() {
        Map<QualifiedSessionId, RenderedPage> pending = new ConcurrentHashMap<>();
        AtomicReference<LocalSessionRegistry> owner = new AtomicReference<>();
        AtomicInteger starts = new AtomicInteger();
        EventLoop loop = new EventLoop() {
            public void start(Runnable logic) { starts.incrementAndGet(); }
            public void stop() { }
        };
        LocalSessionRegistry registry = new LocalSessionRegistry(pending, () -> {
            owner.get().closeAll();
            return loop;
        }, LocalSessionResumeConfig.defaults());
        owner.set(registry);
        CountDownLatch closed = new CountDownLatch(1);
        registry.register(ID, page(closed));
        assertEquals(0, starts.get());
        assertEquals(0, closed.getCount());
        assertEquals(0, registry.size());
        assertTrue(pending.isEmpty());
    }

    @Test
    void shutdownRacingWithStartupClosesThePublishedSession() throws Exception {
        CountDownLatch closing = new CountDownLatch(1);
        Map<QualifiedSessionId, RenderedPage> pending = new ConcurrentHashMap<>() {
            @Override
            public void clear() {
                super.clear();
                closing.countDown();
            }
        };
        CountDownLatch starting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        DefaultEventLoop delegate = new DefaultEventLoop();
        LocalSessionRegistry registry = new LocalSessionRegistry(pending, () -> new EventLoop() {
            public void start(Runnable logic) {
                starting.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new AssertionError("startup not released");
                    }
                } catch (InterruptedException failure) {
                    throw new AssertionError(failure);
                }
                delegate.start(logic);
            }
            public void stop() { delegate.stop(); }
        }, LocalSessionResumeConfig.defaults());
        CountDownLatch closed = new CountDownLatch(1);
        var registration = CompletableFuture.runAsync(() -> registry.register(ID, page(closed)));
        try {
            assertTrue(starting.await(2, TimeUnit.SECONDS));
            assertEquals(1, registry.size(), "ownership must be published before the loop starts");
            var stopping = CompletableFuture.runAsync(registry::closeAll);
            assertTrue(closing.await(2, TimeUnit.SECONDS));
            release.countDown();
            registration.get(2, TimeUnit.SECONDS);
            stopping.get(2, TimeUnit.SECONDS);
            assertTrue(closed.await(2, TimeUnit.SECONDS));
            assertEquals(0, registry.size());
            assertTrue(pending.isEmpty());
        } finally {
            release.countDown();
            registry.closeAll();
        }
    }

    private static LocalSessionRegistry registry(Map<QualifiedSessionId, RenderedPage> pending,
                                                  Duration grace) {
        return new LocalSessionRegistry(pending, DefaultEventLoop::new,
                new LocalSessionResumeConfig(grace, 32, 65_536));
    }

    private static RenderedPage page(CountDownLatch closed) {
        RedirectableEventsConsumer commands = new RedirectableEventsConsumer();
        PageBuilder builder = new PageBuilder(ID, Optional.empty(), new ComponentContext(), commands);
        PageScope scope = new PageScope();
        scope.own(closed::countDown);
        return new RenderedPage(builder, commands, scope);
    }
}
