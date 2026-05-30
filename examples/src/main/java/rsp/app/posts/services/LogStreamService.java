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

public class LogStreamService {

    public static final int DEFAULT_BUFFER_SIZE = 10;

    private final Object lock = new Object();
    private final int bufferSize;
    private final Clock clock;
    private final Random random;
    private final List<LogEntry> entries = new ArrayList<>();
    private final List<Consumer<List<LogEntry>>> subscribers = new CopyOnWriteArrayList<>();

    private long sequence;
    private ScheduledExecutorService scheduler;

    public LogStreamService() {
        this(DEFAULT_BUFFER_SIZE, Clock.systemDefaultZone(), new Random());
    }

    public LogStreamService(final int bufferSize, final Clock clock, final Random random) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must be at least 1");
        }
        this.bufferSize = bufferSize;
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
    }

    public void start() {
        List<LogEntry> initialSnapshot;
        synchronized (lock) {
            if (scheduler != null) {
                return;
            }
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "log-stream");
                thread.setDaemon(true);
                return thread;
            });
            if (entries.isEmpty()) {
                seedInitialBufferLocked();
            }
            initialSnapshot = List.copyOf(entries);
            scheduler.schedule(this::tick, nextDelayMs(), TimeUnit.MILLISECONDS);
        }
        notifySubscribers(initialSnapshot);
    }

    public void stop() {
        synchronized (lock) {
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
        }
    }

    public List<LogEntry> snapshot() {
        synchronized (lock) {
            return List.copyOf(entries);
        }
    }

    public Runnable subscribe(final Consumer<List<LogEntry>> listener) {
        Objects.requireNonNull(listener);
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    public List<LogEntry> emitNextEntry() {
        List<LogEntry> snapshot;
        synchronized (lock) {
            snapshot = emitEntryLocked(clock.instant());
        }
        notifySubscribers(snapshot);
        return snapshot;
    }

    private void tick() {
        emitNextEntry();
        synchronized (lock) {
            if (scheduler != null) {
                scheduler.schedule(this::tick, nextDelayMs(), TimeUnit.MILLISECONDS);
            }
        }
    }

    private long nextDelayMs() {
        if (random.nextDouble() < 0.30) {
            return 100L + random.nextInt(200);
        }
        return 600L + random.nextInt(900);
    }

    private LogEntry.Level pickLevel() {
        double roll = random.nextDouble();
        if (roll < 0.85) {
            return LogEntry.Level.INFO;
        }
        if (roll < 0.97) {
            return LogEntry.Level.WARN;
        }
        return LogEntry.Level.ERROR;
    }

    private void seedInitialBufferLocked() {
        Instant now = clock.instant();
        for (int i = 0; i < bufferSize; i++) {
            Instant timestamp = now.minusMillis((bufferSize - i - 1L) * 800L);
            emitEntryLocked(timestamp);
        }
    }

    private List<LogEntry> emitEntryLocked(final Instant timestamp) {
        long nextSequence = ++sequence;
        LogEntry entry = new LogEntry(
                nextSequence,
                timestamp,
                pickLevel(),
                "Logrem ipsum.. " + nextSequence);
        entries.add(entry);
        while (entries.size() > bufferSize) {
            entries.removeFirst();
        }
        return List.copyOf(entries);
    }

    private void notifySubscribers(final List<LogEntry> snapshot) {
        for (Consumer<List<LogEntry>> subscriber : subscribers) {
            subscriber.accept(snapshot);
        }
    }
}
