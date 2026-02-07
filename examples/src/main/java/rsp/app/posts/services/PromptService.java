package rsp.app.posts.services;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PromptService {

    public record Message(String text, boolean fromUser) {}

    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final AtomicInteger tickCount = new AtomicInteger(0);
    private final List<Consumer<Message>> subscribers = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    /**
     * Subscribe to receive messages (echo replies and ticks).
     *
     * @param listener callback invoked with each new message
     * @return a Runnable to unsubscribe
     */
    public Runnable subscribe(Consumer<Message> listener) {
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    /**
     * Send a prompt. Immediately notifies subscribers with an echo reply.
     *
     * @param text the prompt text
     */
    public void sendPrompt(String text) {
        int count = messageCount.incrementAndGet();
        Message reply = new Message("echo-" + count, false);
        notifySubscribers(reply);
    }

    /**
     * Start sending tick messages every 5 seconds.
     */
    public void startTicking() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "prompt-ticker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            int count = tickCount.incrementAndGet();
            Message tick = new Message("tick-" + count, false);
            notifySubscribers(tick);
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Stop the tick scheduler.
     */
    public void stopTicking() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    private void notifySubscribers(Message message) {
        for (Consumer<Message> subscriber : subscribers) {
            try {
                subscriber.accept(message);
            } catch (Exception e) {
                // Ignore individual subscriber failures
            }
        }
    }
}
