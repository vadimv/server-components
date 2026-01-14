package rsp.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.dom.TreePositionPath;
import rsp.dom.XmlNs;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class ComponentSegmentTests {

    private static final String INITIAL_STATE = "initial";
    private static final String NEW_STATE = "updated";
    private static final TreePositionPath START_DOM_PATH = TreePositionPath.of("1");

    private QualifiedSessionId sessionId;
    private ComponentCompositeKey componentId;
    private ComponentContext componentContext;
    private List<Command> capturedCommands;
    private CommandsEnqueue commandsEnqueue;
    private TestCallbacks callbacks;

    @BeforeEach
    void setUp() {
        sessionId = new QualifiedSessionId("device", "session");
        componentId = new ComponentCompositeKey(
                sessionId,
                "testType",
                TreePositionPath.of("1")
        );
        componentContext = new ComponentContext();
        capturedCommands = new ArrayList<>();
        commandsEnqueue = capturedCommands::add;
        callbacks = new TestCallbacks();
    }

    private TreeBuilder createTreeBuilder() {
        return new TreeBuilder(sessionId, START_DOM_PATH, componentContext, commandsEnqueue);
    }

    private ComponentSegment<String> createSegment(final TreeBuilderFactory factory) {
        return createSegment(INITIAL_STATE, factory);
    }

    private ComponentSegment<String> createSegment(final String initialState, final TreeBuilderFactory factory) {
        final ComponentStateSupplier<String> stateSupplier = (key, ctx) -> initialState;
        final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (ctx, state) -> ctx;
        final ComponentView<String> componentView = stateUpdate -> state -> renderContext -> {
            renderContext.openNode(XmlNs.html, "div", false);
            renderContext.closeNode("div", false);
        };

        return new ComponentSegment<>(
                componentId,
                stateSupplier,
                contextResolver,
                componentView,
                callbacks,
                factory,
                componentContext,
                commandsEnqueue
        );
    }

    private ComponentSegment<String> createSegmentWithEmptyView(final String initialState, final TreeBuilderFactory factory) {
        final ComponentStateSupplier<String> stateSupplier = (key, ctx) -> initialState;
        final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (ctx, state) -> ctx;
        final ComponentView<String> componentView = stateUpdate -> state -> renderContext -> {};

        return new ComponentSegment<>(
                componentId,
                stateSupplier,
                contextResolver,
                componentView,
                callbacks,
                factory,
                componentContext,
                commandsEnqueue
        );
    }

    private void renderSegment(final TreeBuilder treeBuilder, final ComponentSegment<String> segment) {
        treeBuilder.openComponent(segment);
        segment.render(treeBuilder);
        treeBuilder.closeComponent();
    }

    /**
     * A manual test stub for ComponentCallbacks that records callback invocations.
     */
    static class TestCallbacks implements ComponentCallbacks<String> {
        final List<String> callOrder = new ArrayList<>();
        boolean vetoUpdate = false;
        String lastOldState;
        String lastNewState;
        ComponentCompositeKey lastComponentId;

        void clear() {
            callOrder.clear();
            lastOldState = null;
            lastNewState = null;
            lastComponentId = null;
        }

        @Override
        public boolean onBeforeUpdated(final String newState, final CommandsEnqueue commandsEnqueue) {
            callOrder.add("onBeforeUpdated:" + newState);
            return !vetoUpdate;
        }

        @Override
        public void onAfterRendered(final String state, final Subscriber subscriber, final CommandsEnqueue commandsEnqueue, final StateUpdate<String> stateUpdate) {
            callOrder.add("onAfterRendered:" + state);
        }

        @Override
        public void onMounted(final ComponentCompositeKey componentId, final String state, final StateUpdate<String> stateUpdate) {
            callOrder.add("onMounted:" + state);
            this.lastComponentId = componentId;
        }

        @Override
        public void onUpdated(final ComponentCompositeKey componentId, final String oldState, final String newState, final StateUpdate<String> stateUpdate) {
            callOrder.add("onUpdated:" + oldState + "->" + newState);
            this.lastComponentId = componentId;
            this.lastOldState = oldState;
            this.lastNewState = newState;
        }

        @Override
        public void onUnmounted(final ComponentCompositeKey componentId, final String state) {
            callOrder.add("onUnmounted:" + state);
            this.lastComponentId = componentId;
        }
    }

    @Nested
    public class RenderCallbackOrderTests {

        @Test
        void calls_on_after_rendered_before_on_mounted() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegmentWithEmptyView(INITIAL_STATE, treeBuilder);

            renderSegment(treeBuilder, segment);

            assertEquals(List.of("onAfterRendered:" + INITIAL_STATE, "onMounted:" + INITIAL_STATE), callbacks.callOrder);
        }

        @Test
        void passes_correct_state_to_callbacks() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegmentWithEmptyView(INITIAL_STATE, treeBuilder);

            renderSegment(treeBuilder, segment);

            assertTrue(callbacks.callOrder.contains("onAfterRendered:" + INITIAL_STATE));
            assertTrue(callbacks.callOrder.contains("onMounted:" + INITIAL_STATE));
            assertEquals(componentId, callbacks.lastComponentId);
        }
    }

    @Nested
    public class ApplyStateTransformationCallbackOrderTests {

        @Test
        void calls_callbacks_in_correct_order() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegment(treeBuilder);
            renderSegment(treeBuilder, segment);
            callbacks.clear();

            segment.applyStateTransformation(s -> NEW_STATE);

            assertEquals(List.of(
                    "onBeforeUpdated:" + NEW_STATE,
                    "onAfterRendered:" + NEW_STATE,
                    "onUpdated:" + INITIAL_STATE + "->" + NEW_STATE
            ), callbacks.callOrder);
        }

        @Test
        void passes_old_and_new_state_to_on_updated() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegment(treeBuilder);
            renderSegment(treeBuilder, segment);
            callbacks.clear();

            segment.applyStateTransformation(s -> NEW_STATE);

            assertEquals(INITIAL_STATE, callbacks.lastOldState);
            assertEquals(NEW_STATE, callbacks.lastNewState);
            assertEquals(componentId, callbacks.lastComponentId);
        }
    }

    @Nested
    public class VetoMechanismTests {

        @Test
        void when_vetoed_state_update_is_cancelled() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegmentWithEmptyView(INITIAL_STATE, treeBuilder);
            renderSegment(treeBuilder, segment);
            callbacks.clear();
            callbacks.vetoUpdate = true;

            segment.applyStateTransformation(s -> NEW_STATE);

            assertEquals(List.of("onBeforeUpdated:" + NEW_STATE), callbacks.callOrder);
        }

        @Test
        void when_vetoed_no_commands_are_enqueued() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegmentWithEmptyView(INITIAL_STATE, treeBuilder);
            callbacks.vetoUpdate = true;
            renderSegment(treeBuilder, segment);
            capturedCommands.clear();

            segment.applyStateTransformation(s -> NEW_STATE);

            assertTrue(capturedCommands.isEmpty(), "No commands should be enqueued when update is vetoed");
        }
    }

    @Nested
    public class NullStateRejectionTests {

        @Test
        void throws_npe_when_state_transformer_returns_null() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegment(treeBuilder);
            renderSegment(treeBuilder, segment);

            final NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> segment.applyStateTransformation(s -> null)
            );

            assertTrue(exception.getMessage().contains("cannot return null"));
        }

        @Test
        void throws_npe_when_set_state_is_called_with_null() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegmentWithEmptyView(INITIAL_STATE, treeBuilder);
            renderSegment(treeBuilder, segment);

            final NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> segment.setState(null)
            );

            assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        void adds_exception_to_context_when_initial_state_is_null() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentStateSupplier<String> nullStateSupplier = (key, ctx) -> null;
            final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (ctx, state) -> ctx;
            final ComponentView<String> componentView = stateUpdate -> state -> renderContext -> {};

            final ComponentSegment<String> segment = new ComponentSegment<>(
                    componentId,
                    nullStateSupplier,
                    contextResolver,
                    componentView,
                    callbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, segment);

            assertFalse(treeBuilder.exceptions().isEmpty(), "Should have caught an exception");
            assertTrue(treeBuilder.exceptions().get(0) instanceof NullPointerException);
        }
    }

    @Nested
    public class UnmountPropagationTests {

        @Test
        void calls_on_unmounted_when_unmount_is_called() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegmentWithEmptyView(INITIAL_STATE, treeBuilder);
            renderSegment(treeBuilder, segment);
            callbacks.clear();

            segment.unmount();

            assertEquals(List.of("onUnmounted:" + INITIAL_STATE), callbacks.callOrder);
            assertEquals(componentId, callbacks.lastComponentId);
        }

        @Test
        void calls_on_unmounted_with_current_state() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegment(treeBuilder);
            renderSegment(treeBuilder, segment);

            segment.applyStateTransformation(s -> NEW_STATE);
            callbacks.clear();

            segment.unmount();

            assertEquals(List.of("onUnmounted:" + NEW_STATE), callbacks.callOrder);
        }
    }
}
