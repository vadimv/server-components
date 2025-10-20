package rsp.page;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

import static java.lang.System.Logger;

public final class Reactor<T> implements Runnable, Consumer<T> {
    private static final Logger logger = System.getLogger(Reactor.class.getName());

    private final BlockingQueue<T> eventsQueue = new LinkedBlockingDeque<>();
    private final Consumer<T> consumer;

    private volatile boolean isRunning;

    public Reactor(final Consumer<T> consumer) {
        this.consumer = consumer;
    }

    public void start() {
        isRunning = true;
        Thread.startVirtualThread(this);
    }

    public void stop()  {
        isRunning = false;
    }

    @Override
    public void accept(T s) {
        try {
            eventsQueue.put(s);
        } catch (InterruptedException e) {
            logger.log(Logger.Level.ERROR, "Event loop queue put InterruptedException", e);
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                final T event = eventsQueue.take();
                consumer.accept(event);
            } catch (final InterruptedException e) {
                // no-op
            } catch (final Throwable e) {
                logger.log(Logger.Level.ERROR, "Event loop error", e);
            }
        }
    }
}
