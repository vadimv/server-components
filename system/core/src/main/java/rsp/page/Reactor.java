package rsp.page;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

import static java.lang.System.Logger;

/**
 * A Reactor is responsible for processing a sequential queue of events for a single component session.
 * It ensures that events are processed one at a time in a single-threaded manner.
 * The execution of the event loop is delegated to an {@link EventLoop}.
 * @param <T> the type of events in the queue
 */
public final class Reactor<T> implements Consumer<T> {
    private static final Logger logger = System.getLogger(Reactor.class.getName());

    private final BlockingQueue<T> eventsQueue = new LinkedBlockingDeque<>();
    private final Consumer<T> consumer;
    private final EventLoop eventLoop;

    /**
     * Creates a new Reactor.
     * @param consumer the business logic that consumes events
     * @param eventLoop the execution context that runs the event loop
     */
    public Reactor(final Consumer<T> consumer, final EventLoop eventLoop) {
        this.consumer = consumer;
        this.eventLoop = eventLoop;
    }

    /**
     * Starts the event loop by delegating to the {@link EventLoop}.
     * This will begin the process of repeatedly executing the event processing step.
     */
    public void start() {
        eventLoop.start(this::processNextEvent);
    }

    /**
     * Stops the event loop by delegating to the {@link EventLoop}.
     */
    public void stop()  {
        eventLoop.stop();
    }

    /**
     * Accepts a new event and adds it to the processing queue.
     * This method is thread-safe.
     * @param s the event to be processed
     */
    @Override
    public void accept(T s) {
        try {
            eventsQueue.put(s);
        } catch (InterruptedException e) {
            logger.log(Logger.Level.ERROR, "Event loop queue put InterruptedException", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Checks if the event queue is empty.
     * This is primarily useful for testing to avoid blocking when manually stepping through events.
     * @return true if the queue is empty, false otherwise
     */
    public boolean isEmpty() {
        return eventsQueue.isEmpty();
    }

    /**
     * Processes the next available event from the queue.
     * This method will block until an event is available.
     * This represents a single step in the event loop's execution.
     */
    private void processNextEvent() {
        try {
            final T event = eventsQueue.take(); // This blocks until an event is ready
            consumer.accept(event);
        } catch (final InterruptedException e) {
            // This is an expected part of the shutdown sequence, where the blocking
            // call to take() is interrupted by the EventLoop's stop() method.
            Thread.currentThread().interrupt();
        } catch (final Throwable e) {
            logger.log(Logger.Level.ERROR, "Event loop error", e);
        }
    }
}
