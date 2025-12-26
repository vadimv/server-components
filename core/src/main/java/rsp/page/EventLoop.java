package rsp.page;

/**
 * An abstraction for running a sequential, long-running event loop.
 */
public interface EventLoop {
    /**
     * Starts the provided loop logic.
     * In production, this will likely start a new thread.
     * In a deterministic test, this might do nothing, allowing the test
     * to control the execution.
     * @param logic the loop's logic
     */
    void start(Runnable logic);

    /**
     * Stops the event loop.
     */
    void stop();
}
