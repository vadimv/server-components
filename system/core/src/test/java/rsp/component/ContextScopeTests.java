package rsp.component;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ContextScopeTests {

    private static final ContextKey.StringKey<String> KEY =
            new ContextKey.StringKey<>("scope.key", String.class);
    private static final ContextKey.StringKey<String> OTHER =
            new ContextKey.StringKey<>("scope.other", String.class);

    @Test
    void replace_notifies_watcher_when_value_changes() {
        final ContextScope scope = new ContextScope(new ComponentContext().with(KEY, "old"));
        final AtomicReference<String> oldValue = new AtomicReference<>();
        final AtomicReference<String> newValue = new AtomicReference<>();

        scope.watch(KEY, (oldV, newV) -> {
            oldValue.set(oldV);
            newValue.set(newV);
        });

        scope.replace(new ComponentContext().with(KEY, "new"));

        assertEquals("old", oldValue.get());
        assertEquals("new", newValue.get());
    }

    @Test
    void replace_does_not_notify_when_value_is_equal() {
        final ContextScope scope = new ContextScope(new ComponentContext().with(KEY, "same"));
        final AtomicInteger calls = new AtomicInteger();

        scope.watch(KEY, (_, _) -> calls.incrementAndGet());
        scope.replace(new ComponentContext().with(KEY, "same"));

        assertEquals(0, calls.get());
    }

    @Test
    void replace_checks_only_watched_keys() {
        final ContextScope scope = new ContextScope(new ComponentContext().with(KEY, "same"));
        final AtomicInteger calls = new AtomicInteger();

        scope.watch(KEY, (_, _) -> calls.incrementAndGet());
        scope.replace(new ComponentContext().with(KEY, "same").with(OTHER, "changed"));

        assertEquals(0, calls.get());
    }

    @Test
    void current_context_is_replaced_before_watchers_run() {
        final ContextScope scope = new ContextScope(new ComponentContext().with(KEY, "old"));
        final AtomicReference<String> observedCurrent = new AtomicReference<>();

        scope.watch(KEY, (_, _) -> observedCurrent.set(scope.current().get(KEY)));
        scope.replace(new ComponentContext().with(KEY, "new"));

        assertEquals("new", observedCurrent.get());
    }

    @Test
    void registration_unsubscribes_watcher() {
        final ContextScope scope = new ContextScope(new ComponentContext().with(KEY, "old"));
        final AtomicInteger calls = new AtomicInteger();

        final Lookup.Registration registration = scope.watch(KEY, (_, _) -> calls.incrementAndGet());
        registration.unsubscribe();
        scope.replace(new ComponentContext().with(KEY, "new"));

        assertEquals(0, calls.get());
    }

    @Test
    void clear_removes_watchers() {
        final ContextScope scope = new ContextScope(new ComponentContext().with(KEY, "old"));
        final AtomicInteger calls = new AtomicInteger();

        scope.watch(KEY, (_, _) -> calls.incrementAndGet());
        scope.clear();
        scope.replace(new ComponentContext().with(KEY, "new"));

        assertEquals(0, calls.get());
    }

    @Test
    void controller_replaces_and_clears_owned_scope() {
        final ContextScope.Controller controller =
                ContextScope.controller(new ComponentContext().with(KEY, "old"));
        final AtomicReference<String> observed = new AtomicReference<>();

        controller.scope().watch(KEY, (_, newValue) -> observed.set(newValue));
        controller.replace(new ComponentContext().with(KEY, "new"));

        assertEquals("new", controller.scope().current().get(KEY));
        assertEquals("new", observed.get());

        controller.clear();
        controller.replace(new ComponentContext().with(KEY, "later"));

        assertEquals("new", observed.get());
    }
}
