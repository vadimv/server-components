package rsp.actor.ui;

import rsp.actor.ActorRef;
import rsp.actor.ActorSystem;
import rsp.actor.ActorType;
import rsp.actor.SendResult;
import rsp.component.ComponentSegment;
import rsp.component.ContextKey;
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
 * Optional application-scoped directory for one actor of a given type per page.
 * Entries are discoverable while their page is alive and removed when it closes.
 * The actor remains owned by the supplied {@link ActorSystem}; the directory
 * requests passivation by sending the supplied close message.
 */
public final class PageActorDirectory<K, M> {
    private static final System.Logger logger = System.getLogger(PageActorDirectory.class.getName());

    public record Entry<K, M>(K id, ActorRef<M> ref) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(ref, "ref");
        }
    }

    private final ActorSystem actors;
    private final ActorType<K, M> type;
    private final Supplier<K> ids;
    private final Supplier<M> closeMessage;
    private final Map<QualifiedSessionId, Entry<K, M>> byPage = new HashMap<>();
    private final Map<QualifiedSessionId, PageScope> pageScopes = new HashMap<>();
    private final Map<K, Entry<K, M>> byId = new LinkedHashMap<>();

    public PageActorDirectory(ActorSystem actors, ActorType<K, M> type,
                              Supplier<K> ids, Supplier<M> closeMessage) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.type = Objects.requireNonNull(type, "type");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.closeMessage = Objects.requireNonNull(closeMessage, "closeMessage");
    }

    /** Convenience for numeric REST resource IDs, starting at one. */
    public static <M> PageActorDirectory<Long, M> numbered(ActorSystem actors,
                                                            ActorType<Long, M> type,
                                                            Supplier<M> closeMessage) {
        AtomicLong nextId = new AtomicLong();
        return new PageActorDirectory<>(actors, type,
                () -> nextId.updateAndGet(current -> {
                    if (current == Long.MAX_VALUE) {
                        throw new IllegalStateException("Page actor ID space exhausted");
                    }
                    return current + 1;
                }), closeMessage);
    }

    /** Opens or reuses the actor for a mounted component's page. */
    public Entry<K, M> forPage(QualifiedSessionId pageId, ComponentSegment<?> segment) {
        Objects.requireNonNull(segment, "segment");
        PageScope scope = segment.componentContext()
                .getRequired(new ContextKey.ClassKey<>(PageScope.class));
        return forPage(pageId, scope);
    }

    /** Opens or reuses the actor for a page, registering its close with the page scope. */
    public synchronized Entry<K, M> forPage(QualifiedSessionId pageId, PageScope scope) {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(scope, "scope");
        Entry<K, M> existing = byPage.get(pageId);
        if (existing != null) {
            if (pageScopes.get(pageId) != scope) {
                throw new IllegalStateException("Page actor belongs to a different page scope");
            }
            return existing;
        }
        K id = Objects.requireNonNull(ids.get(), "page actor id");
        if (byId.containsKey(id)) {
            throw new IllegalStateException("Duplicate page actor ID: " + id);
        }
        Entry<K, M> entry = new Entry<>(id, actors.ref(type, id));
        byPage.put(pageId, entry);
        pageScopes.put(pageId, scope);
        byId.put(id, entry);
        scope.own(() -> closeIfCurrent(pageId, entry));
        return entry;
    }

    public synchronized Optional<Entry<K, M>> find(K id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }

    /** Snapshot in creation order. */
    public synchronized List<Entry<K, M>> all() {
        return List.copyOf(new ArrayList<>(byId.values()));
    }

    /** Explicitly closes one page actor before its page ends. Idempotent. */
    public void close(QualifiedSessionId pageId) {
        closeIfCurrent(Objects.requireNonNull(pageId, "pageId"), null);
    }

    private void closeIfCurrent(QualifiedSessionId pageId, Entry<K, M> expected) {
        final Entry<K, M> entry;
        synchronized (this) {
            Entry<K, M> current = byPage.get(pageId);
            if (current == null || (expected != null && current != expected)) {
                return;
            }
            entry = current;
            byPage.remove(pageId);
            pageScopes.remove(pageId);
            byId.remove(entry.id());
        }
        SendResult result = entry.ref().tell(Objects.requireNonNull(closeMessage.get(), "close message"));
        if (result != SendResult.ACCEPTED) {
            logger.log(System.Logger.Level.WARNING,
                    "Page actor close was not admitted [actorType=" + type.name()
                            + ", reason=" + result + "]");
        }
    }
}
