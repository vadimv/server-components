package rsp.app.posts.services;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PromptService {

    /**
     * Scope for message history and subscriptions.
     * Defaults to per-session id, but can be extended to user/device/etc.
     */
    public enum Scope {
        SESSION_ID
    }

    public record Message(String text, boolean fromUser, boolean update) {
        public Message(String text, boolean fromUser) {
            this(text, fromUser, false);
        }
    }

    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final AtomicInteger tickCount = new AtomicInteger(0);
    private final ConcurrentMap<String, List<Consumer<Message>>> subscribersByScope = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<Message>> messageHistoryByScope = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    /**
     * Subscribe to receive messages (echo replies and ticks).
     *
     * @param listener callback invoked with each new message
     * @return a Runnable to unsubscribe
     */
    public Runnable subscribe(String scopeKey, Consumer<Message> listener) {
        subscribersByScope.computeIfAbsent(scopeKey, _ -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> {
            List<Consumer<Message>> listeners = subscribersByScope.get(scopeKey);
            if (listeners != null) {
                listeners.remove(listener);
            }
        };
    }

    /**
     * Send a prompt. Immediately notifies subscribers with an echo reply.
     *
     * @param text the prompt text
     */
    public void sendPrompt(String scopeKey, String text) {
        history(scopeKey).add(new Message(text, true));
        int count = messageCount.incrementAndGet();
        Message reply = new Message("echo-" + count, false);
       // notifySubscribers(scopeKey, reply);
    }

    /**
     * Get the full message history (user prompts + service replies + ticks).
     */
    public List<Message> getMessageHistory(String scopeKey) {
        return List.copyOf(history(scopeKey));
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
            // Broadcast tick to all active scopes
            for (String scopeKey : subscribersByScope.keySet()) {
              //  notifySubscribers(scopeKey, tick);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Send a reply from the agent (not from the user).
     * Records in history and notifies subscribers.
     *
     * @param scopeKey the session scope key
     * @param text     the reply text
     */
    public void sendReply(String scopeKey, String text) {
        Message reply = new Message(text, false);
        notifySubscribers(scopeKey, reply);
    }

    /**
     * Update the last non-user message in history (for streaming token accumulation).
     * Replaces the text of the most recent system message and notifies subscribers
     * with {@code update=true}.
     */
    public void updateLastReply(String scopeKey, String text) {
        List<Message> hist = history(scopeKey);
        // Replace last non-user message in history
        for (int i = hist.size() - 1; i >= 0; i--) {
            if (!hist.get(i).fromUser()) {
                hist.set(i, new Message(text, false, true));
                break;
            }
        }
        // Notify with update flag
        Message update = new Message(text, false, true);
        List<Consumer<Message>> subscribers = subscribersByScope.get(scopeKey);
        if (subscribers == null) {
            return;
        }
        for (Consumer<Message> subscriber : subscribers) {
            try {
                subscriber.accept(update);
            } catch (Exception e) {
                // Ignore individual subscriber failures
            }
        }
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

    private void notifySubscribers(String scopeKey, Message message) {
        history(scopeKey).add(message);
        List<Consumer<Message>> subscribers = subscribersByScope.get(scopeKey);
        if (subscribers == null) {
            return;
        }
        for (Consumer<Message> subscriber : subscribers) {
            try {
                subscriber.accept(message);
            } catch (Exception e) {
                // Ignore individual subscriber failures
            }
        }
    }

    private List<Message> history(String scopeKey) {
        return messageHistoryByScope.computeIfAbsent(scopeKey, _ -> new CopyOnWriteArrayList<>());
    }
}
