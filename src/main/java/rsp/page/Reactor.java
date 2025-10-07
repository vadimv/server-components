package rsp.page;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

public class Reactor<T> implements Runnable, Consumer<T> {
    private final BlockingQueue<T> eventsQueue = new LinkedBlockingDeque<>();
    private final Consumer<T> consumer;

    private volatile boolean isRunning;

    public Reactor(final Consumer<T> consumer) {
        this.consumer = consumer;
    }

    public void start() {
        isRunning = true;
        Thread.ofVirtual().start(this);
    }

    public void stop()  {
        isRunning = false;
    }

    @Override
    public void accept(T s) {
        try {
            eventsQueue.put(s);
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                final T event = eventsQueue.take();
                consumer.accept(event);
            } catch (InterruptedException e) {}
        }

    }
}
