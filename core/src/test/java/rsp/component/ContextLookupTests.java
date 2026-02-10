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
import java.util.function.BiConsumer;
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
     */
    private static class RecordingSubscriber implements Subscriber {
        private int typedHandlerCount = 0;
        private int voidHandlerCount = 0;

        @Override
        public void addWindowEventHandler(String eventType, Consumer<EventContext> eventHandler,
                                          boolean preventDefault, DomEventEntry.Modifier modifier) {
            // Not used in ContextLookup
        }

        @Override
        public void addComponentEventHandler(String eventType, Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {
            // Track as typed handler
            typedHandlerCount++;
        }

        @Override
        public <T> void addEventHandler(EventKey<T> key, BiConsumer<String, T> handler, boolean preventDefault) {
            typedHandlerCount++;
        }

        @Override
        public void addEventHandler(EventKey.VoidKey key, Runnable handler, boolean preventDefault) {
            voidHandlerCount++;
        }

        @Override
        public void removeComponentEventHandler(String eventType) {
            // No-op for test
        }

        public int getTypedHandlerCount() {
            return typedHandlerCount;
        }

        public int getVoidHandlerCount() {
            return voidHandlerCount;
        }
    }
}
