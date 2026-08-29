package rsp.compositions.agent;

import rsp.compositions.block.BlockActionPayload;


import rsp.compositions.block.BlockAction;
import rsp.compositions.block.PayloadSchema;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.component.Lookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ActionDispatcherTests {

    private ActionDispatcher dispatcher;
    private ActionGate allowAllGate;

    @BeforeEach
    void setUp() {
        dispatcher = new ActionDispatcher();
        allowAllGate = (action, payload, lookup) -> new GateResult.Allow(action, payload);
    }

    @Test
    void dispatches_void_action() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.void");
        List<String> published = new ArrayList<>();

        Lookup blockLookup = new StubLookup() {
            @Override
            public void publish(EventKey.VoidKey k) {
                published.add(k.name());
            }
        };

        BlockAction action = new BlockAction("do_thing", key, "Do a thing");
        StubBlock block = new StubBlock(List.of(action), blockLookup);

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, BlockActionPayload.EMPTY, block, new StubLookup(), allowAllGate);

        assertInstanceOf(ActionDispatcher.DispatchResult.Dispatched.class, result);
        assertEquals(List.of("test.void"), published);
    }

    @Test
    void dispatches_simple_key_action_with_payload() {
        EventKey.SimpleKey<String> key = new EventKey.SimpleKey<>("test.simple", String.class);
        List<Object> published = new ArrayList<>();

        Lookup blockLookup = new StubLookup() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void publish(EventKey<T> k, T payload) {
                published.add(payload);
            }
        };

        BlockAction action = new BlockAction("edit", key, "Edit item",
            new PayloadSchema.StringValue("id"));
        StubBlock block = new StubBlock(List.of(action), blockLookup);

        dispatcher.dispatch(action, BlockActionPayload.of("42"), block, new StubLookup(), allowAllGate);

        assertEquals(List.of("42"), published);
    }

    @Test
    void returns_blocked_when_gate_blocks() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.delete");
        BlockAction action = new BlockAction("delete", key, "Delete items");
        ActionGate blockGate = (a, p, lookup) -> new GateResult.Block("Not permitted");

        StubBlock block = new StubBlock(List.of(action));

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, BlockActionPayload.EMPTY, block, new StubLookup(), blockGate);

        assertInstanceOf(ActionDispatcher.DispatchResult.Blocked.class, result);
        assertEquals("Not permitted",
            ((ActionDispatcher.DispatchResult.Blocked) result).reason());
    }

    @Test
    void returns_awaiting_confirmation_when_gate_confirms() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.delete");
        BlockAction action = new BlockAction("delete", key, "Delete items");
        ActionGate confirmGate = (a, p, lookup) ->
            new GateResult.Confirm("Are you sure?", a, p);

        StubBlock block = new StubBlock(List.of(action));

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, BlockActionPayload.EMPTY, block, new StubLookup(), confirmGate);

        assertInstanceOf(ActionDispatcher.DispatchResult.AwaitingConfirmation.class, result);
        assertEquals("Are you sure?",
            ((ActionDispatcher.DispatchResult.AwaitingConfirmation) result).question());
    }

    @Test
    void returns_payload_error_when_parser_rejects() {
        @SuppressWarnings("unchecked")
        EventKey.SimpleKey<Set<String>> key = new EventKey.SimpleKey<>("test.delete",
                (Class<Set<String>>) (Class<?>) Set.class);

        BlockAction action = new BlockAction("delete", key, "Delete items",
            new PayloadSchema.StringSet("IDs"));
        StubBlock block = new StubBlock(List.of(action));

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, BlockActionPayload.of(true), block, new StubLookup(), allowAllGate);

        assertInstanceOf(ActionDispatcher.DispatchResult.PayloadError.class, result);
        ActionDispatcher.DispatchResult.PayloadError pe =
            (ActionDispatcher.DispatchResult.PayloadError) result;
        assertEquals("delete", pe.action());
        assertTrue(pe.message().contains("Boolean"));
    }

    @Test
    void parse_payload_converts_string_to_set() {
        @SuppressWarnings("unchecked")
        EventKey.SimpleKey<Set<String>> key = new EventKey.SimpleKey<>("test.delete",
                (Class<Set<String>>) (Class<?>) Set.class);
        List<Object> published = new ArrayList<>();

        Lookup blockLookup = new StubLookup() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void publish(EventKey<T> k, T payload) {
                published.add(payload);
            }
        };

        BlockAction action = new BlockAction("delete", key, "Delete items",
            new PayloadSchema.StringSet("IDs"));
        StubBlock block = new StubBlock(List.of(action), blockLookup);

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, BlockActionPayload.of("1"), block, new StubLookup(), allowAllGate);

        assertInstanceOf(ActionDispatcher.DispatchResult.Dispatched.class, result);
        assertEquals(List.of(Set.of("1")), published);
    }

    @Test
    void dispatch_direct_bypasses_gate() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.direct");
        List<String> published = new ArrayList<>();

        Lookup blockLookup = new StubLookup() {
            @Override
            public void publish(EventKey.VoidKey k) {
                published.add(k.name());
            }
        };

        BlockAction action = new BlockAction("act", key, "An action");
        StubBlock block = new StubBlock(List.of(action), blockLookup);

        dispatcher.dispatchDirect(action, BlockActionPayload.EMPTY, block);

        assertEquals(List.of("test.direct"), published);
    }

    @Test
    void dispatch_navigate_publishes_the_configured_block_key() {
        final Object postsKey = new Object();
        final List<Object> published = new ArrayList<>();
        final Lookup lookup = new StubLookup() {
            @Override
            public <T> void publish(EventKey<T> key, T payload) {
                published.add(payload);
            }
        };

        dispatcher.dispatchNavigate(postsKey, lookup);

        assertEquals(1, published.size());
        assertSame(postsKey, published.getFirst());
    }

    // --- Stubs ---

    /** Minimal block stub with a test-controlled active lookup. */
    private static class StubBlock implements rsp.compositions.block.BlockRuntime {
        private final List<BlockAction> actions;
        private final Lookup lookup;

        StubBlock(List<BlockAction> actions) {
            this(actions, new StubLookup());
        }

        StubBlock(List<BlockAction> actions, Lookup lookup) {
            this.actions = actions;
            this.lookup = lookup;
        }

        @Override
        public List<BlockAction> agentActions() {
            return actions;
        }

        @Override
        public Lookup lookup() {
            return lookup;
        }

        @Override
        public String title() {
            return "Stub";
        }
    }

    /** Minimal Lookup stub — overridden per test for publish tracking. */
    private static class StubLookup implements Lookup {
        @Override public <T> T get(rsp.component.ContextKey<T> key) { return null; }
        @Override public <T> T get(Class<T> key) { return null; }
        @Override public <T> T getRequired(rsp.component.ContextKey<T> key) { throw new IllegalStateException(); }
        @Override public <T> T getRequired(Class<T> key) { throw new IllegalStateException(); }
        @Override public <T> Lookup with(rsp.component.ContextKey<T> key, T value) { return this; }
        @Override public <T> Lookup with(Class<T> clazz, T instance) { return this; }
        @Override public <T> void publish(EventKey<T> key, T payload) {}
        @Override public void publish(EventKey.VoidKey key) {}
        @Override public void enqueueTask(Runnable task) { task.run(); }
        @Override public <T> Registration subscribe(EventKey<T> key, java.util.function.BiConsumer<String, T> handler) {
            return () -> {};
        }
        @Override public Registration subscribe(EventKey.VoidKey key, Runnable handler) {
            return () -> {};
        }
    }
}
