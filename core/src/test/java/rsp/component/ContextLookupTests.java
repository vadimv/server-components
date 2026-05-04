package rsp.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;
import rsp.page.events.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ContextLookup facade implementation.
 * Verifies that ContextLookup correctly delegates to underlying infrastructure.
 */
public class ContextLookupTests {

    private ComponentContext context;
    private RecordingCommandsEnqueue commandsEnqueue;
    private RecordingSubscriber subscriber;
    private ContextLookup lookup;

    @BeforeEach
    void setUp() {
        context = new ComponentContext();
        commandsEnqueue = new RecordingCommandsEnqueue();
        subscriber = new RecordingSubscriber();
        lookup = new ContextLookup(context, commandsEnqueue, subscriber);
    }

    @Nested
    class DataAccessTests {

        @Test
        void get_with_context_key_delegates_to_context() {
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("test.key", String.class);
            final ComponentContext contextWithValue = context.with(key, "hello");
            final ContextLookup lookupWithValue = new ContextLookup(contextWithValue, commandsEnqueue, subscriber);

            assertEquals("hello", lookupWithValue.get(key));
        }

        @Test
        void get_with_class_key_delegates_to_context() {
            final ComponentContext contextWithValue = context.with(String.class, "world");
            final ContextLookup lookupWithValue = new ContextLookup(contextWithValue, commandsEnqueue, subscriber);

            assertEquals("world", lookupWithValue.get(String.class));
        }

        @Test
        void get_missing_key_returns_null() {
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("missing.key", String.class);

            assertNull(lookup.get(key));
        }

        @Test
        void getRequired_with_present_key_returns_value() {
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("test.key", String.class);
            final ComponentContext contextWithValue = context.with(key, "required-value");
            final ContextLookup lookupWithValue = new ContextLookup(contextWithValue, commandsEnqueue, subscriber);

            assertEquals("required-value", lookupWithValue.getRequired(key));
        }

        @Test
        void getRequired_with_missing_key_throws() {
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("missing.key", String.class);

            assertThrows(IllegalStateException.class, () -> lookup.getRequired(key));
        }

        @Test
        void getRequired_with_missing_class_throws() {
            assertThrows(IllegalStateException.class, () -> lookup.getRequired(Runnable.class));
        }
    }

    @Nested
    class ContextCreationTests {

        @Test
        void with_context_key_returns_new_lookup_with_value() {
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("new.key", String.class);

            final Lookup newLookup = lookup.with(key, "new-value");

            assertNotSame(lookup, newLookup);
            assertEquals("new-value", newLookup.get(key));
            assertNull(lookup.get(key)); // Original unchanged
        }

        @Test
        void with_class_key_returns_new_lookup_with_value() {
            final Lookup newLookup = lookup.with(Integer.class, 42);

            assertNotSame(lookup, newLookup);
            assertEquals(42, newLookup.get(Integer.class));
            assertNull(lookup.get(Integer.class)); // Original unchanged
        }

        @Test
        void with_preserves_event_infrastructure() {
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("test.key", String.class);
            final Lookup newLookup = lookup.with(key, "value");

            // Publish through new lookup - should use same commandsEnqueue
            final EventKey.VoidKey eventKey = new EventKey.VoidKey("test.event");
            newLookup.publish(eventKey);

            assertEquals(1, commandsEnqueue.getCommands().size());
        }

        @Test
        void chained_with_calls_accumulate_values() {
            final ContextKey.StringKey<String> key1 = new ContextKey.StringKey<>("key1", String.class);
            final ContextKey.StringKey<Integer> key2 = new ContextKey.StringKey<>("key2", Integer.class);

            final Lookup chained = lookup
                .with(key1, "first")
                .with(key2, 2);

            assertEquals("first", chained.get(key1));
            assertEquals(2, chained.get(key2));
        }
    }

    @Nested
    class EventPublishingTests {

        @Test
        void publish_void_event_offers_to_commands_enqueue() {
            final EventKey.VoidKey key = new EventKey.VoidKey("modal.closed");

            lookup.publish(key);

            assertEquals(1, commandsEnqueue.getCommands().size());
        }

        @Test
        void publish_simple_event_offers_to_commands_enqueue() {
            final EventKey.SimpleKey<String> key = new EventKey.SimpleKey<>("form.submitted", String.class);

            lookup.publish(key, "payload");

            assertEquals(1, commandsEnqueue.getCommands().size());
        }

        @Test
        void publish_dynamic_key_throws_for_base_key() {
            final EventKey.DynamicKey<String> key = new EventKey.DynamicKey<>("state.updated", String.class);

            assertThrows(IllegalArgumentException.class, () -> lookup.publish(key, "value"));
        }

        @Test
        void publish_dynamic_key_with_extension_succeeds() {
            final EventKey.DynamicKey<String> baseKey = new EventKey.DynamicKey<>("state.updated", String.class);
            final EventKey.SimpleKey<String> specificKey = baseKey.with("sort");

            lookup.publish(specificKey, "asc");

            assertEquals(1, commandsEnqueue.getCommands().size());
        }
    }

    @Nested
    class EventSubscriptionTests {

        @Test
        void subscribe_typed_event_registers_handler() {
            final EventKey.SimpleKey<Map> key = new EventKey.SimpleKey<>("form.submitted", Map.class);
            final AtomicReference<Map> received = new AtomicReference<>();

            lookup.subscribe(key, (name, payload) -> received.set(payload));

            assertEquals(1, subscriber.getTypedHandlerCount());
        }

        @Test
        void subscribe_void_event_registers_handler() {
            final EventKey.VoidKey key = new EventKey.VoidKey("close.requested");
            final AtomicBoolean called = new AtomicBoolean(false);

            lookup.subscribe(key, () -> called.set(true));

            assertEquals(1, subscriber.getVoidHandlerCount());
        }

        @Test
        void subscribe_returns_registration() {
            final EventKey.VoidKey key = new EventKey.VoidKey("test.event");

            final Lookup.Registration registration = lookup.subscribe(key, () -> {});

            assertNotNull(registration);
        }
    }

    /**
     * Regression tests for the "lost handler on parent re-render" bug.
     *
     * <p>The previous {@code ContextLookup.subscribe} implementation
     * unsubscribed by calling {@code subscriber.removeComponentEventHandler(key.name())},
     * which removes <em>any</em> handler registered for that event name —
     * including handlers belonging to other (newer) subscribers. When a parent
     * re-rendered, the framework first cleared the parent's
     * {@code componentEventEntries}, then the new child mounted and
     * subscribed (handler-B), then the old child unmounted and unsubscribed
     * by name — wiping handler-B and silently breaking event delivery for
     * the rest of the new child's lifetime.</p>
     *
     * <p>The fix is to return a reference-based {@link Lookup.Registration}
     * from {@code subscribe}, so unsubscribe removes only the specific
     * handler it was paired with.</p>
     */
    @Nested
    class RegistrationTests {

        @Test
        void unsubscribe_removes_only_its_own_handler() {
            final EventKey.VoidKey key = new EventKey.VoidKey("prompt.newMessage");

            final AtomicBoolean firstCalled = new AtomicBoolean(false);
            final AtomicBoolean secondCalled = new AtomicBoolean(false);

            final Lookup.Registration first = lookup.subscribe(key, () -> firstCalled.set(true));
            lookup.subscribe(key, () -> secondCalled.set(true));

            assertEquals(2, subscriber.handlerCountFor(key.name()),
                "Both handlers should be registered");

            first.unsubscribe();

            assertEquals(1, subscriber.handlerCountFor(key.name()),
                "Only the first handler should be removed");

            subscriber.fire(key.name(), java.util.Map.of());

            assertFalse(firstCalled.get(), "First handler must NOT be called after its unsubscribe");
            assertTrue(secondCalled.get(), "Second handler MUST still be called");
        }

        @Test
        void typed_subscribe_unsubscribe_targets_specific_handler() {
            final EventKey.SimpleKey<String> key =
                new EventKey.SimpleKey<>("form.submitted", String.class);

            final List<String> firstReceived = new ArrayList<>();
            final List<String> secondReceived = new ArrayList<>();

            final Lookup.Registration first =
                lookup.subscribe(key, (name, payload) -> firstReceived.add(payload));
            lookup.subscribe(key, (name, payload) -> secondReceived.add(payload));

            first.unsubscribe();
            subscriber.fire(key.name(), "hello");

            assertTrue(firstReceived.isEmpty(), "first handler unsubscribed, must not receive");
            assertEquals(List.of("hello"), secondReceived,
                "second handler must still receive");
        }

        @Test
        void parent_rerender_simulation_does_not_drop_new_handler() {
            // Simulates: parent.componentEventEntries.clear() + new mount subscribes
            // (handler-B) + old unmount unsubscribes (which used to wipe handler-B).
            final EventKey.VoidKey key = new EventKey.VoidKey("prompt.newMessage");

            // Step 1: old PromptView subscribes (handler-A).
            final AtomicBoolean handlerACalled = new AtomicBoolean(false);
            final Lookup.Registration regA = lookup.subscribe(key, () -> handlerACalled.set(true));

            // Step 2: parent re-renders -> componentEventEntries cleared (the entry
            // for handler-A is gone from the subscriber's storage).
            // Simulate by clearing the recording subscriber's storage directly:
            subscriber.handlersByName.clear();

            // Step 3: new PromptView mounts and subscribes (handler-B).
            final AtomicBoolean handlerBCalled = new AtomicBoolean(false);
            lookup.subscribe(key, () -> handlerBCalled.set(true));
            assertEquals(1, subscriber.handlerCountFor(key.name()),
                "After re-render and new mount, handler-B should be the sole entry");

            // Step 4: old PromptView unmounts and calls regA.unsubscribe().
            // With the OLD remove-by-name behaviour this would remove handler-B and
            // leave the subscriber with zero handlers -> persistent silent loss.
            // With the FIX (reference-based removal) it must be a no-op since
            // handler-A's entry was already cleared in step 2.
            regA.unsubscribe();

            assertEquals(1, subscriber.handlerCountFor(key.name()),
                "After old unsubscribe, handler-B must still be present "
                + "(this is the regression assertion)");

            subscriber.fire(key.name(), java.util.Map.of());
            assertFalse(handlerACalled.get(), "handler-A must not be called");
            assertTrue(handlerBCalled.get(),
                "handler-B must still receive events after old PromptView unmounts");
        }
    }

    @Nested
    class ConstructorValidationTests {

        @Test
        void constructor_rejects_null_context() {
            assertThrows(NullPointerException.class,
                () -> new ContextLookup(null, commandsEnqueue, subscriber));
        }

        @Test
        void constructor_rejects_null_commandsEnqueue() {
            assertThrows(NullPointerException.class,
                () -> new ContextLookup(context, null, subscriber));
        }

        @Test
        void constructor_rejects_null_subscriber() {
            assertThrows(NullPointerException.class,
                () -> new ContextLookup(context, commandsEnqueue, null));
        }
    }

    // ===== Test Doubles =====

    /**
     * Recording implementation of CommandsEnqueue for test verification.
     */
    private static class RecordingCommandsEnqueue implements CommandsEnqueue {
        private final List<Command> commands = new ArrayList<>();

        @Override
        public void offer(Command command) {
            commands.add(command);
        }

        public List<Command> getCommands() {
            return commands;
        }
    }

    /**
     * Recording implementation of Subscriber for test verification.
     *
     * <p>Stores added handlers in insertion order keyed by event name and
     * returns reference-based Registrations so removal removes only the
     * specific handler that was added (matching production
     * ComponentSegment behaviour). This is the model the regression
     * tests in {@code RegistrationTests} below depend on.</p>
     */
    private static class RecordingSubscriber implements Subscriber {
        private final java.util.Map<String, List<Consumer<ComponentEventEntry.EventContext>>> handlersByName =
                new java.util.LinkedHashMap<>();

        @Override
        public void addWindowEventHandler(String eventType, Consumer<EventContext> eventHandler,
                                          boolean preventDefault, DomEventEntry.Modifier modifier) {
            // Not used in ContextLookup
        }

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            handlersByName.computeIfAbsent(eventType, _ -> new ArrayList<>()).add(eventHandler);
            return () -> {
                List<Consumer<ComponentEventEntry.EventContext>> handlers = handlersByName.get(eventType);
                if (handlers != null) {
                    handlers.remove(eventHandler);
                }
            };
        }

        public int getTypedHandlerCount() {
            return handlersByName.values().stream().mapToInt(List::size).sum();
        }

        public int getVoidHandlerCount() {
            return getTypedHandlerCount();
        }

        public int handlerCountFor(String eventType) {
            List<Consumer<ComponentEventEntry.EventContext>> handlers = handlersByName.get(eventType);
            return handlers == null ? 0 : handlers.size();
        }

        /**
         * Fire all handlers registered for the given event type with the given payload.
         * Returns the number of handlers that received the event.
         */
        public int fire(String eventType, Object payload) {
            List<Consumer<ComponentEventEntry.EventContext>> handlers = handlersByName.get(eventType);
            if (handlers == null) return 0;
            int delivered = 0;
            // Snapshot so handlers can mutate the list during dispatch.
            for (Consumer<ComponentEventEntry.EventContext> h : new ArrayList<>(handlers)) {
                h.accept(new ComponentEventEntry.EventContext(eventType, payload));
                delivered++;
            }
            return delivered;
        }
    }
}
