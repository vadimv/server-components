package rsp.http;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.page.EventLoop;
import rsp.page.PageBuilder;
import rsp.page.QualifiedSessionId;
import rsp.page.RedirectableEventsConsumer;
import rsp.page.RenderedPage;
import rsp.page.events.RemoteCommand;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumablePageSessionTests {
    private static final QualifiedSessionId SESSION_ID = new QualifiedSessionId("device", "session");
    private static final LocalSessionResumeConfig CONFIG =
            new LocalSessionResumeConfig(Duration.ofSeconds(60), 32, 64 * 1024);

    @Test
    void detached_session_replays_unacknowledged_messages_on_a_new_transport() throws Exception {
        final Fixture fixture = new Fixture(CONFIG);
        final FakeTransport firstTransport = new FakeTransport();
        final ResumablePageSession.AttachResult firstAttach = fixture.session.attach(firstTransport, 0);

        assertTrue(firstAttach.accepted());
        assertEquals(List.of("[17,1,[0,0]]", "[18,1]"), firstTransport.messages);

        fixture.session.acknowledge(firstAttach.handle(), 1);
        fixture.session.detach(firstAttach.handle());
        fixture.commands.offer(new RemoteCommand.PushHistory("/after-detach"));
        fixture.eventLoop.runOneStep();

        final FakeTransport resumedTransport = new FakeTransport();
        final ResumablePageSession.AttachResult resumed = fixture.session.attach(resumedTransport, 1);

        assertTrue(resumed.accepted());
        assertEquals(List.of("[17,2,[6,4,\"/after-detach\"]]", "[18,2]"), resumedTransport.messages);
        assertFalse(fixture.session.isClosed());
    }

    @Test
    void close_from_replaced_transport_does_not_detach_current_transport() {
        final Fixture fixture = new Fixture(CONFIG);
        final FakeTransport firstTransport = new FakeTransport();
        final ResumablePageSession.AttachResult firstAttach = fixture.session.attach(firstTransport, 0);
        final FakeTransport replacementTransport = new FakeTransport();
        final ResumablePageSession.AttachResult replacement = fixture.session.attach(replacementTransport, 0);

        fixture.session.detach(firstAttach.handle());
        fixture.commands.offer(new RemoteCommand.PushHistory("/current"));
        fixture.eventLoop.runOneStep();

        assertTrue(replacement.accepted());
        assertFalse(replacementTransport.closed);
        assertTrue(replacementTransport.messages.contains("[17,2,[6,4,\"/current\"]]"));
        assertTrue(firstTransport.closed);
        assertEquals(4000, firstTransport.closeCode);
    }

    @Test
    void detached_session_expires_and_stops_its_event_loop() {
        final Fixture fixture = new Fixture(CONFIG);
        final FakeTransport transport = new FakeTransport();
        final ResumablePageSession.AttachResult attach = fixture.session.attach(transport, 0);
        fixture.session.detach(attach.handle());

        fixture.scheduler.runPending();
        fixture.eventLoop.runOneStep();

        assertTrue(fixture.session.isClosed());
        assertTrue(fixture.eventLoop.stopped);
        assertEquals(1, fixture.closedCount.get());
    }

    @Test
    void detached_replay_buffer_overflow_closes_the_page() {
        final Fixture fixture = new Fixture(new LocalSessionResumeConfig(Duration.ofSeconds(60), 1, 64 * 1024));

        fixture.commands.offer(new RemoteCommand.PushHistory("/overflow"));
        fixture.eventLoop.runOneStep();

        assertTrue(fixture.session.isClosed());
        assertEquals(1, fixture.closedCount.get());
    }

    @Test
    void attached_session_uses_a_sliding_replay_window_instead_of_closing() {
        final Fixture fixture = new Fixture(new LocalSessionResumeConfig(Duration.ofSeconds(60), 1, 64 * 1024));
        final FakeTransport firstTransport = new FakeTransport();
        final ResumablePageSession.AttachResult firstAttach = fixture.session.attach(firstTransport, 0);

        fixture.commands.offer(new RemoteCommand.PushHistory("/delivered"));
        fixture.eventLoop.runOneStep();

        assertFalse(fixture.session.isClosed());
        assertTrue(firstTransport.messages.contains("[17,2,[6,4,\"/delivered\"]]"));

        fixture.session.detach(firstAttach.handle());
        assertFalse(fixture.session.attach(new FakeTransport(), 0).accepted());

        final FakeTransport resumedTransport = new FakeTransport();
        final ResumablePageSession.AttachResult resumed = fixture.session.attach(resumedTransport, 1);

        assertTrue(resumed.accepted());
        assertEquals(List.of("[17,2,[6,4,\"/delivered\"]]", "[18,2]"), resumedTransport.messages);
    }

    @Test
    void an_attached_frame_larger_than_the_byte_window_does_not_close_the_page() {
        final Fixture fixture = new Fixture(new LocalSessionResumeConfig(Duration.ofSeconds(60), 32, 20));
        final FakeTransport firstTransport = new FakeTransport();
        final ResumablePageSession.AttachResult firstAttach = fixture.session.attach(firstTransport, 0);

        fixture.commands.offer(new RemoteCommand.PushHistory("/larger-than-window"));
        fixture.eventLoop.runOneStep();

        assertFalse(fixture.session.isClosed());
        assertTrue(firstTransport.messages.contains("[17,2,[6,4,\"/larger-than-window\"]]"));

        fixture.session.detach(firstAttach.handle());
        final FakeTransport resumedTransport = new FakeTransport();
        final ResumablePageSession.AttachResult resumed = fixture.session.attach(resumedTransport, 2);

        assertTrue(resumed.accepted());
        assertEquals(List.of("[18,2]"), resumedTransport.messages);
    }

    private static final class Fixture {
        private final ManualEventLoop eventLoop = new ManualEventLoop();
        private final ManualExpiryScheduler scheduler = new ManualExpiryScheduler();
        private final AtomicInteger closedCount = new AtomicInteger();
        private final RedirectableEventsConsumer commands = new RedirectableEventsConsumer();
        private final ResumablePageSession session;

        private Fixture(final LocalSessionResumeConfig config) {
            final PageBuilder pageBuilder = new PageBuilder(SESSION_ID,
                                                            "/* test config */",
                                                            new ComponentContext(),
                                                            commands);
            session = new ResumablePageSession(SESSION_ID,
                                               new RenderedPage(pageBuilder, commands),
                                               eventLoop,
                                               config,
                                               scheduler,
                                               _ -> closedCount.incrementAndGet());
            // Process InitSessionCommand; its initial empty ListenEvent is handled synchronously.
            eventLoop.runOneStep();
        }
    }

    private static final class FakeTransport implements ResumablePageSession.Transport {
        private final List<String> messages = new ArrayList<>();
        private boolean closed;
        private int closeCode;

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void sendText(final String text) throws IOException {
            if (closed) {
                throw new IOException("closed");
            }
            messages.add(text);
        }

        @Override
        public void close(final int code, final String reason) {
            closeCode = code;
            closed = true;
        }

        @Override
        public void closeSocket() {
            closed = true;
        }
    }

    private static final class ManualExpiryScheduler implements ResumablePageSession.ExpiryScheduler {
        private ScheduledTask pending;

        @Override
        public ResumablePageSession.ExpiryTask schedule(final Runnable task, final Duration delay) {
            pending = new ScheduledTask(task);
            return pending;
        }

        private void runPending() {
            final ScheduledTask task = pending;
            if (task != null && !task.cancelled) {
                task.runnable.run();
            }
        }

        private static final class ScheduledTask implements ResumablePageSession.ExpiryTask {
            private final Runnable runnable;
            private boolean cancelled;

            private ScheduledTask(final Runnable runnable) {
                this.runnable = runnable;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        }
    }

    private static final class ManualEventLoop implements EventLoop {
        private Runnable step;
        private boolean stopped;

        @Override
        public void start(final Runnable logic) {
            step = logic;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        private void runOneStep() {
            if (!stopped) {
                step.run();
            }
        }
    }
}
