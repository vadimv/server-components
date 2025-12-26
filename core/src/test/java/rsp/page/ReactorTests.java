package rsp.page;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ReactorTests {

    /**
     * This integration test verifies that the Reactor, when combined with the DefaultEventLoop,
     * processes events asynchronously on a virtual thread.
     */
    @Test
    void processes_events_asynchronously_when_using_default_event_loop() throws InterruptedException {
        // 1. Setup
        final CountDownLatch eventProcessedLatch = new CountDownLatch(1);
        final List<String> receivedEvents = new ArrayList<>();
        final Consumer<String> mockConsumer = event -> {
            receivedEvents.add(event);
            eventProcessedLatch.countDown();
        };

        final EventLoop eventLoop = new DefaultEventLoop();
        final Reactor<String> reactor = new Reactor<>(mockConsumer, eventLoop);

        // 2. Act
        reactor.start();
        reactor.accept("test-event-1");

        // 3. Assert
        assertTrue(eventProcessedLatch.await(2, TimeUnit.SECONDS),
                   "The event should have been processed within the timeout.");

        assertEquals(1, receivedEvents.size());
        assertEquals("test-event-1", receivedEvents.get(0));

        // 4. Cleanup
        reactor.stop();
    }

    /**
     * This unit test demonstrates how a manual, deterministic EventLoop can be used
     * to test the Reactor's behavior one step at a time, without any real concurrency.
     */
    @Test
    void processes_one_event_per_step_when_using_manual_event_loop() {
        // 1. Setup
        final List<String> receivedEvents = new ArrayList<>();
        final ManualEventLoop manualEventLoop = new ManualEventLoop();
        final Reactor<String> reactor = new Reactor<>(receivedEvents::add, manualEventLoop);

        // 2. Act & Assert
        reactor.start(); // This just captures the step logic, doesn't start a thread

        // No events have been processed yet
        assertTrue(receivedEvents.isEmpty());

        // Add an event to the reactor's queue
        reactor.accept("event-A");

        // Still no events processed, because we haven't stepped the loop
        assertTrue(receivedEvents.isEmpty());

        // Manually drive one step of the event loop
        manualEventLoop.runOneStep();

        // Now the event should be processed
        assertEquals(1, receivedEvents.size());
        assertEquals("event-A", receivedEvents.get(0));

        // Add two more events
        reactor.accept("event-B");
        reactor.accept("event-C");

        // Use the isEmpty() method to safely drain the queue
        while (!reactor.isEmpty()) {
            manualEventLoop.runOneStep();
        }

        // Assert that all events are now processed in order
        assertEquals(3, receivedEvents.size());
        assertEquals(List.of("event-A", "event-B", "event-C"), receivedEvents);
    }

    /**
     * A manual, deterministic implementation of the EventLoop for testing.
     * It captures the event processing step and allows the test to execute it
     * on demand by calling {@link #runOneStep()}.
     */
    private static class ManualEventLoop implements EventLoop {
        private Runnable step;

        @Override
        public void start(Runnable logic) {
            this.step = logic;
        }

        @Override
        public void stop() {
            // In a real test, you might want to add logic here,
            // e.g., to check if the loop is stopped correctly.
        }

        /**
         * Executes one single step of the event loop.
         * This will block if the reactor's queue is empty, so tests
         * must ensure an event is available before calling runOneStep.
         */
        public void runOneStep() {
            if (step == null) {
                throw new IllegalStateException("EventLoop not started or no logic provided.");
            }
            step.run();
        }
    }
}
