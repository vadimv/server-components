package rsp.compositions.agentui;

import rsp.application.ApplicationLifecycle;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static rsp.util.SafeDiagnostics.failure;

public class PromptService implements ApplicationLifecycle {

    private static final System.Logger logger = System.getLogger(PromptService.class.getName());

    /**
     * Scope for message history and subscriptions.
     * Defaults to per-session id, but can be extended to user/device/etc.
     */
    public enum Scope {
        SESSION_ID
    }

    public record Message(long id, String text, boolean fromUser, boolean update) {
        public Message(long id, String text, boolean fromUser) {
            this(id, text, fromUser, false);
        }
    }

    private final AtomicLong messageIdGenerator = new AtomicLong(0);
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
        List<Consumer<Message>> listeners = subscribersByScope.computeIfAbsent(scopeKey, _ -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("Prompt subscriber added [totalListeners=%d]", listeners.size()));
        return () -> {
            List<Consumer<Message>> current = subscribersByScope.get(scopeKey);
            if (current != null) {
                boolean removed = current.remove(listener);
                logger.log(System.Logger.Level.DEBUG,
                    () -> String.format("Prompt subscriber removed [removed=%s, remainingListeners=%d]",
                                        removed, current.size()));
            } else {
                logger.log(System.Logger.Level.DEBUG, "Prompt subscriber removal skipped; scope has no listeners");
            }
        };
    }

    /**
     * Send a prompt. Immediately notifies subscribers with an echo reply.
     *
     * @param text the prompt text
     */
    public void sendPrompt(String scopeKey, String text) {
        Message userMsg = new Message(messageIdGenerator.incrementAndGet(), text, true);
        history(scopeKey).add(userMsg);
        int count = messageCount.incrementAndGet();
        Message reply = new Message(messageIdGenerator.incrementAndGet(), "echo-" + count, false);
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("User prompt persisted [messageId=%d, textChars=%d, listeners=%d, notifying=false]",
                                userMsg.id(), text == null ? 0 : text.length(),
                                listenerCount(scopeKey)));
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
            Message tick = new Message(messageIdGenerator.incrementAndGet(), "tick-" + count, false);
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
        Message reply = new Message(messageIdGenerator.incrementAndGet(), text, false);
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("Agent reply created [messageId=%d, textChars=%d, listeners=%d]",
                                reply.id(), text == null ? 0 : text.length(), listenerCount(scopeKey)));
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
                hist.set(i, new Message(hist.get(i).id(), text, false, true));
                break;
            }
        }
        // Notify with update flag — reuse the same id as the message being updated
        long updateId = messageIdGenerator.incrementAndGet();
        Message update = new Message(updateId, text, false, true);
        List<Consumer<Message>> subscribers = subscribersByScope.get(scopeKey);
        int subCount = subscribers == null ? 0 : subscribers.size();
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("Agent reply updated [messageId=%d, textChars=%d, listeners=%d]",
                                updateId, text == null ? 0 : text.length(), subCount));
        if (subscribers == null) {
            return;
        }
        for (Consumer<Message> subscriber : subscribers) {
            try {
                subscriber.accept(update);
            } catch (Exception e) {
                logger.log(System.Logger.Level.WARNING,
                    () -> failure("Prompt reply update subscriber failed", e));
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

    @Override
    public void start() {
        startTicking();
    }

    @Override
    public void stop() {
        stopTicking();
    }

    private void notifySubscribers(String scopeKey, Message message) {
        history(scopeKey).add(message);
        List<Consumer<Message>> subscribers = subscribersByScope.get(scopeKey);
        int subCount = subscribers == null ? 0 : subscribers.size();
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("Prompt message notifying subscribers [messageId=%d, fromUser=%s, listeners=%d]",
                                message.id(), message.fromUser(), subCount));
        if (subscribers == null) {
            return;
        }
        int idx = 0;
        for (Consumer<Message> subscriber : subscribers) {
            final int currentIdx = idx++;
            try {
                logger.log(System.Logger.Level.DEBUG,
                    () -> String.format("Prompt subscriber notification [index=%d, messageId=%d]",
                                        currentIdx, message.id()));
                subscriber.accept(message);
            } catch (Exception e) {
                logger.log(System.Logger.Level.WARNING,
                    () -> failure("Prompt subscriber notification failed", e));
            }
        }
    }

    private int listenerCount(String scopeKey) {
        List<Consumer<Message>> subs = subscribersByScope.get(scopeKey);
        return subs == null ? 0 : subs.size();
    }

    private List<Message> history(String scopeKey) {
        return messageHistoryByScope.computeIfAbsent(scopeKey, _ -> new CopyOnWriteArrayList<>());
    }
}
