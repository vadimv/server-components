package rsp.app.gameoflife;

import rsp.actor.ActorDefinition;
import rsp.actor.ActorAskTimeoutException;
import rsp.actor.ActorDeliveryException;
import rsp.actor.SendResult;
import rsp.actor.runtime.ActorSnapshot;
import rsp.actor.runtime.ActorScheduler;
import rsp.actor.runtime.ActorView;
import rsp.actor.runtime.LocalActorSystem;
import rsp.actor.runtime.OwnedActor;
import rsp.application.ApplicationLifecycle;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;

/** Application-owned simulations, usable from Java, HTTP, or another facade. */
public final class Simulations implements ApplicationLifecycle {
    public record Simulation(long id, ActorView<LifeGame.State, LifeGame.Command> view) {
        public ActorSnapshot<LifeGame.State> snapshot() { return view.snapshot(); }
    }

    private final LocalActorSystem actors;
    private final ActorDefinition<LifeGame.State, LifeGame.Command> definition;
    private final LongSupplier ids;
    private final int capacity;
    private final Duration timeout;
    private final ActorScheduler deadlines;
    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private boolean running;
    private boolean stopped;

    public Simulations(LocalActorSystem actors, ActorDefinition<LifeGame.State, LifeGame.Command> definition,
                       LongSupplier ids, int capacity, Duration timeout, ActorScheduler deadlines) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.ids = Objects.requireNonNull(ids, "ids");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.deadlines = Objects.requireNonNull(deadlines, "deadlines");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
    }

    @Override
    public synchronized void start() {
        if (stopped) throw new IllegalStateException("Simulations cannot be restarted");
        running = true;
    }

    /** Completes only after START has committed; creation is independent of any viewer. */
    public CompletionStage<Simulation> create() {
        final Entry entry;
        try {
            synchronized (this) {
                requireRunning();
                if (entries.size() >= capacity) throw new CapacityExceeded();
                long id = ids.getAsLong();
                if (id <= 0 || entries.containsKey(id)) throw new IllegalStateException("Invalid or duplicate simulation ID");
                entry = new Entry(id);
                entries.put(id, entry);
            }
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        try {
            var owner = actors.createOwned(LifeGame.TYPE.id(entry.id), definition);
            if (!entry.attach(owner)) {
                owner.close();
                return entry.created.minimalCompletionStage();
            }
            owner.view().observeSnapshots(snapshot -> {
                if (!snapshot.active()) remove(entry, new ActorDeliveryException(SendResult.STOPPED));
            });
            var deadline = deadlines.schedule(timeout,
                    () -> remove(entry, new ActorAskTimeoutException()));
            entry.created.whenComplete((_, _) -> deadline.cancel());
            owner.view().ref().track(LifeGame.Control.of(LifeGame.Action.START)).processed()
                    .whenComplete((_, failure) -> {
                        if (failure != null) {
                            remove(entry, failure);
                            return;
                        }
                        final boolean publish;
                        synchronized (Simulations.this) {
                            publish = running && entries.get(entry.id) == entry && owner.view().snapshot().active();
                            if (publish) entry.published = true;
                        }
                        if (publish) entry.created.complete(new Simulation(entry.id, owner.view()));
                        else remove(entry, new ActorDeliveryException(SendResult.STOPPED));
                    });
        } catch (RuntimeException | Error failure) {
            remove(entry, failure);
        }
        return entry.created.minimalCompletionStage();
    }

    public synchronized Optional<Simulation> find(long id) {
        Entry entry = entries.get(id);
        if (!running || entry == null || !entry.published || !entry.owner.view().snapshot().active()) {
            return Optional.empty();
        }
        return Optional.of(new Simulation(id, entry.owner.view()));
    }

    public synchronized List<Simulation> list() {
        return entries.keySet().stream().map(this::find).flatMap(Optional::stream).toList();
    }

    public ActorSnapshot<LifeGame.State> snapshot(long id) {
        return require(id).snapshot();
    }

    public CompletionStage<LifeGame.GameSummary> control(long id, LifeGame.Action action) {
        try {
            var simulation = require(id);
            return actors.ask(simulation.view().ref(), reply -> LifeGame.Control.replying(action, reply), timeout);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    public void delete(long id) {
        final Entry entry;
        synchronized (this) {
            require(id);
            entry = entries.remove(id);
        }
        entry.close(new ActorDeliveryException(SendResult.STOPPED));
    }

    private Simulation require(long id) {
        return find(id).orElseThrow(() -> new MissingSimulation(id));
    }

    private void requireRunning() {
        if (!running) throw new ActorDeliveryException(stopped ? SendResult.STOPPED : SendResult.NOT_STARTED);
    }

    private void remove(Entry entry, Throwable failure) {
        synchronized (this) {
            entries.remove(entry.id, entry);
        }
        entry.close(failure);
    }

    @Override
    public void stop() {
        final List<Entry> closing;
        synchronized (this) {
            if (stopped) return;
            stopped = true;
            running = false;
            closing = List.copyOf(entries.values());
            entries.clear();
        }
        closing.forEach(entry -> entry.close(new ActorDeliveryException(SendResult.STOPPED)));
    }

    public static final class MissingSimulation extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public MissingSimulation(long id) { super("Unknown simulation: " + id); }
    }

    public static final class CapacityExceeded extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public CapacityExceeded() { super("Shared simulation capacity reached"); }
    }

    private static final class Entry {
        final long id;
        final CompletableFuture<Simulation> created = new CompletableFuture<>();
        volatile OwnedActor<LifeGame.State, LifeGame.Command> owner;
        boolean published; // Guarded by the service registry lock.
        private boolean closed;

        Entry(long id) { this.id = id; }

        synchronized boolean attach(OwnedActor<LifeGame.State, LifeGame.Command> value) {
            if (closed) return false;
            owner = value;
            return true;
        }

        void close(Throwable failure) {
            final OwnedActor<LifeGame.State, LifeGame.Command> closing;
            synchronized (this) {
                if (closed) return;
                closed = true;
                closing = owner;
            }
            if (closing != null) closing.close();
            created.completeExceptionally(failure);
        }
    }
}
