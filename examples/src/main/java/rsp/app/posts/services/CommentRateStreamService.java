package rsp.app.posts.services;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CommentRateStreamService {

    public static final int DEFAULT_WINDOW_SIZE = 30;

    public record Sample(long sequence, Instant timestamp, int value) {
        public Sample {
            timestamp = Objects.requireNonNull(timestamp);
            value = Math.max(0, value);
        }
    }

    private final Object lock = new Object();
    private final IntSampleSource source;
    private final int windowSize;
    private final Clock clock;
    private final List<Sample> samples = new ArrayList<>();
    private final List<Consumer<List<Sample>>> subscribers = new CopyOnWriteArrayList<>();

    private long sequence;
    private ScheduledExecutorService scheduler;

    public CommentRateStreamService() {
        this(OuSpikeSampleSource.commentsRateDefaults(new Random()),
                DEFAULT_WINDOW_SIZE,
                Clock.systemDefaultZone());
    }

    public CommentRateStreamService(final List<Integer> demoValues,
                                    final int windowSize,
                                    final Clock clock) {
        this(new CyclingSampleSource(demoValues), windowSize, clock);
    }

    public CommentRateStreamService(final IntSampleSource source,
                                    final int windowSize,
                                    final Clock clock) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("windowSize must be at least 1");
        }
        this.source = Objects.requireNonNull(source, "source");
        this.windowSize = windowSize;
        this.clock = Objects.requireNonNull(clock);
    }

    public void start() {
        List<Sample> initialSnapshot = null;
        synchronized (lock) {
            if (scheduler != null) {
                return;
            }
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "comment-rate-stream");
                thread.setDaemon(true);
                return thread;
            });
            if (samples.isEmpty()) {
                initialSnapshot = seedInitialWindowLocked();
            }
            scheduler.scheduleAtFixedRate(() -> emitNextSample(), 1, 1, TimeUnit.SECONDS);
        }
        if (initialSnapshot != null) {
            notifySubscribers(initialSnapshot);
        }
    }

    public void stop() {
        synchronized (lock) {
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
        }
    }

    public List<Sample> snapshot() {
        synchronized (lock) {
            return List.copyOf(samples);
        }
    }

    public Runnable subscribe(final Consumer<List<Sample>> listener) {
        Objects.requireNonNull(listener);
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    public List<Sample> emitNextSample() {
        List<Sample> snapshot;
        synchronized (lock) {
            snapshot = emitSampleLocked(clock.instant());
        }
        notifySubscribers(snapshot);
        return snapshot;
    }

    private List<Sample> seedInitialWindowLocked() {
        int initialSampleCount = source.initialWindowSize(windowSize);
        Instant now = clock.instant();
        List<Sample> snapshot = List.of();
        for (int i = 0; i < initialSampleCount; i++) {
            Instant timestamp = now.minusSeconds(initialSampleCount - i - 1L);
            snapshot = emitSampleLocked(timestamp);
        }
        return snapshot;
    }

    private List<Sample> emitSampleLocked(final Instant timestamp) {
        long nextSequence = ++sequence;
        int value = source.next();
        samples.add(new Sample(nextSequence, timestamp, value));
        while (samples.size() > windowSize) {
            samples.removeFirst();
        }
        return List.copyOf(samples);
    }

    private void notifySubscribers(final List<Sample> snapshot) {
        for (Consumer<List<Sample>> subscriber : subscribers) {
            subscriber.accept(snapshot);
        }
    }
}
