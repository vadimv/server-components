package rsp.compositions.agent;

import rsp.compositions.contract.AgentAction;
import rsp.compositions.contract.PayloadSchema;


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

        Lookup contractLookup = new StubLookup() {
            @Override
            public void publish(EventKey.VoidKey k) {
                published.add(k.name());
            }
        };

        AgentAction action = new AgentAction("do_thing", key, "Do a thing");
        StubContract contract = new StubContract(List.of(action), contractLookup);

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, AgentPayload.EMPTY, contract, new StubLookup(), allowAllGate);

        assertInstanceOf(ActionDispatcher.DispatchResult.Dispatched.class, result);
        assertEquals(List.of("test.void"), published);
    }

    @Test
    void dispatches_simple_key_action_with_payload() {
        EventKey.SimpleKey<String> key = new EventKey.SimpleKey<>("test.simple", String.class);
        List<Object> published = new ArrayList<>();

        Lookup contractLookup = new StubLookup() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void publish(EventKey<T> k, T payload) {
                published.add(payload);
            }
        };

        AgentAction action = new AgentAction("edit", key, "Edit item",
            new PayloadSchema.StringValue("id"));
        StubContract contract = new StubContract(List.of(action), contractLookup);

        dispatcher.dispatch(action, AgentPayload.of("42"), contract, new StubLookup(), allowAllGate);

        assertEquals(List.of("42"), published);
    }

    @Test
    void returns_blocked_when_gate_blocks() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.delete");
        AgentAction action = new AgentAction("delete", key, "Delete items");
        ActionGate blockGate = (a, p, lookup) -> new GateResult.Block("Not permitted");

        StubContract contract = new StubContract(List.of(action));

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, AgentPayload.EMPTY, contract, new StubLookup(), blockGate);

        assertInstanceOf(ActionDispatcher.DispatchResult.Blocked.class, result);
        assertEquals("Not permitted",
            ((ActionDispatcher.DispatchResult.Blocked) result).reason());
    }

    @Test
    void returns_awaiting_confirmation_when_gate_confirms() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.delete");
        AgentAction action = new AgentAction("delete", key, "Delete items");
        ActionGate confirmGate = (a, p, lookup) ->
            new GateResult.Confirm("Are you sure?", a, p);

        StubContract contract = new StubContract(List.of(action));

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, AgentPayload.EMPTY, contract, new StubLookup(), confirmGate);

        assertInstanceOf(ActionDispatcher.DispatchResult.AwaitingConfirmation.class, result);
        assertEquals("Are you sure?",
            ((ActionDispatcher.DispatchResult.AwaitingConfirmation) result).question());
    }

    @Test
    void returns_payload_error_when_parser_rejects() {
        @SuppressWarnings("unchecked")
        EventKey.SimpleKey<Set<String>> key = new EventKey.SimpleKey<>("test.delete",
                (Class<Set<String>>) (Class<?>) Set.class);

        AgentAction action = new AgentAction("delete", key, "Delete items",
            new PayloadSchema.StringSet("IDs"));
        StubContract contract = new StubContract(List.of(action));

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, AgentPayload.of(true), contract, new StubLookup(), allowAllGate);

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

        Lookup contractLookup = new StubLookup() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> void publish(EventKey<T> k, T payload) {
                published.add(payload);
            }
        };

        AgentAction action = new AgentAction("delete", key, "Delete items",
            new PayloadSchema.StringSet("IDs"));
        StubContract contract = new StubContract(List.of(action), contractLookup);

        ActionDispatcher.DispatchResult result = dispatcher.dispatch(
            action, AgentPayload.of("1"), contract, new StubLookup(), allowAllGate);

        assertInstanceOf(ActionDispatcher.DispatchResult.Dispatched.class, result);
        assertEquals(List.of(Set.of("1")), published);
    }

    @Test
    void dispatch_direct_bypasses_gate() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.direct");
        List<String> published = new ArrayList<>();

        Lookup contractLookup = new StubLookup() {
            @Override
            public void publish(EventKey.VoidKey k) {
                published.add(k.name());
            }
        };

        AgentAction action = new AgentAction("act", key, "An action");
        StubContract contract = new StubContract(List.of(action), contractLookup);

        dispatcher.dispatchDirect(action, AgentPayload.EMPTY, contract);

        assertEquals(List.of("test.direct"), published);
    }

    // --- Stubs ---

    /** Minimal ViewContract stub for testing. */
    private static class StubContract extends rsp.compositions.contract.ViewContract {
        private final List<AgentAction> actions;

        StubContract(List<AgentAction> actions) {
            this(actions, new StubLookup());
        }

        StubContract(List<AgentAction> actions, Lookup lookup) {
            super(lookup);
            this.actions = actions;
        }

        @Override
        public List<AgentAction> agentActions() {
            return actions;
        }

        @Override
        public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext ctx) {
            return ctx;
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
