package rsp.page;

/**
 * The default production implementation of an {@link EventLoop}.
 * It runs the provided event processing step in a continuous loop on a new virtual thread.
 */
public final class DefaultEventLoop implements EventLoop {
    private static final System.Logger logger = System.getLogger(DefaultEventLoop.class.getName());

    private volatile Thread thread;
    private volatile boolean isRunning = false;

    /**
     * Starts a new virtual thread that repeatedly executes the provided {@code step} logic
     * in a loop until {@link #stop()} is called.
     * @param step the single unit of work to be executed repeatedly.
     */
    @Override
    public void start(final Runnable step) {
        isRunning = true;
        this.thread = Thread.startVirtualThread(() -> {
            while (isRunning) {
                step.run();
            }
        });
    }

    /**
     * Stops the event loop by signaling the loop to terminate and interrupting the thread.
     * This method is non-blocking and does not wait for the thread to actually exit.
     */
    @Override
    public void stop() {
        isRunning = false;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }
}
