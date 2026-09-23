package rsp.actor.ui;

import rsp.actor.ActorDefinition;
import rsp.actor.ActorRef;
import rsp.actor.ActorType;
import rsp.component.CommandsEnqueue;
import rsp.page.PageScope;
import rsp.page.QualifiedSessionId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Application-scoped catalog and owner-selection policy for one page-hosted
 * actor of a chosen type per logical page.
 */
public final class PageActorDirectory<K, M> {
    public record Entry<K, M>(K id, ActorRef<M> ref) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(ref, "ref");
        }
    }

    private record Binding<K, M>(Entry<K, M> entry,
                                 PageActorHandle<?, M> handle,
                                 PageScope scope,
                                 ActorDefinition<?, M> definition,
                                 Object generation) {
    }

    private final PageActorRuntime runtime;
    private final ActorType<K, M> type;
    private final Supplier<K> ids;
    private final Map<QualifiedSessionId, Binding<K, M>> byPage = new HashMap<>();
    private final Map<K, Binding<K, M>> byId = new LinkedHashMap<>();

    public PageActorDirectory(PageActorRuntime runtime, ActorType<K, M> type,
                              Supplier<K> ids) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.type = Objects.requireNonNull(type, "type");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /** Convenience for numeric REST resource IDs, starting at one. */
    public static <M> PageActorDirectory<Long, M> numbered(
            PageActorRuntime runtime, ActorType<Long, M> type) {
        AtomicLong nextId = new AtomicLong();
        return new PageActorDirectory<>(runtime, type,
                () -> nextId.updateAndGet(current -> {
                    if (current == Long.MAX_VALUE) {
                        throw new IllegalStateException("Page actor ID space exhausted");
                    }
                    return current + 1;
                }));
    }

    /**
     * Opens or reuses this page's activation. The page scope owns the
     * activation; repeated component mounts receive the same handle.
     */
    public synchronized <S> PageActorHandle<S, M> activate(
            QualifiedSessionId pageId,
            PageScope scope,
            CommandsEnqueue commands,
            ActorDefinition<S, M> definition) {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(definition, "definition");
        if (!type.equals(definition.type())) {
            throw new IllegalArgumentException("Actor definition type does not match directory");
        }

        Binding<K, M> existing = byPage.get(pageId);
        if (existing != null) {
            if (existing.scope() != scope) {
                throw new IllegalStateException("Page actor belongs to a different page scope");
            }
            if (existing.definition() != definition) {
                throw new IllegalStateException(
                        "Page actor was activated with a different definition");
            }
            @SuppressWarnings("unchecked")
            PageActorHandle<S, M> handle = (PageActorHandle<S, M>) existing.handle();
            return handle;
        }

        K id = Objects.requireNonNull(ids.get(), "page actor id");
        if (byId.containsKey(id)) {
            throw new IllegalStateException("Duplicate page actor ID: " + id);
        }
        Object generation = new Object();
        PageActorHandle<S, M> handle = runtime.activate(type.id(id), definition,
                commands, scope, () -> removeIfCurrent(pageId, generation));
        Entry<K, M> entry = new Entry<>(id, handle.ref());
        Binding<K, M> binding = new Binding<>(entry, handle, scope, definition, generation);
        byPage.put(pageId, binding);
        byId.put(id, binding);
        return handle;
    }

    public synchronized Optional<Entry<K, M>> find(K id) {
        Binding<K, M> binding = byId.get(Objects.requireNonNull(id, "id"));
        return binding == null ? Optional.empty() : Optional.of(binding.entry());
    }

    /** Snapshot in creation order. */
    public synchronized List<Entry<K, M>> all() {
        ArrayList<Entry<K, M>> entries = new ArrayList<>(byId.size());
        byId.values().forEach(binding -> entries.add(binding.entry()));
        return List.copyOf(entries);
    }

    /** Explicitly closes one activation before its page ends. Idempotent. */
    public void close(QualifiedSessionId pageId) {
        Objects.requireNonNull(pageId, "pageId");
        final Binding<K, M> binding;
        synchronized (this) {
            binding = byPage.remove(pageId);
            if (binding != null) {
                byId.remove(binding.entry().id(), binding);
            }
        }
        if (binding != null) {
            binding.handle().close();
        }
    }

    private synchronized void removeIfCurrent(QualifiedSessionId pageId, Object generation) {
        Binding<K, M> current = byPage.get(pageId);
        if (current == null || current.generation() != generation) {
            return;
        }
        byPage.remove(pageId);
        byId.remove(current.entry().id(), current);
    }
}
