package rsp.http;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.page.DefaultEventLoop;
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
        assertTrue(registry.findOrCreate(ID).isEmpty());
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
    void websocketHandoffCancelsPendingExpiryAndClosesOnSessionEnd() throws Exception {
        Map<QualifiedSessionId, RenderedPage> pending = new ConcurrentHashMap<>();
        LocalSessionRegistry registry = registry(pending, Duration.ofMillis(150));
        CountDownLatch closed = new CountDownLatch(1);
        registry.register(ID, page(closed));

        var live = registry.findOrCreate(ID).orElseThrow();
        assertFalse(closed.await(50, TimeUnit.MILLISECONDS));
        live.close("test");
        assertTrue(closed.await(2, TimeUnit.SECONDS));
        registry.closeAll();
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
