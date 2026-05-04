package rsp.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.dom.TreePositionPath;
import rsp.dom.TagNode;
import rsp.dom.Node;
import rsp.dom.XmlNs;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class ComponentSegmentTests {

    private static final String INITIAL_STATE = "initial";
    private static final String NEW_STATE = "updated";
    private static final TreePositionPath START_DOM_PATH = TreePositionPath.of("1");
    private static final ComponentRuntimePolicy REUSABLE_POLICY = new ComponentRuntimePolicy() {
        @Override
        public boolean isReusable() {
            return true;
        }
    };

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
    public class ParentTagSyncTests {

        private ComponentSegment<String> createChildSegment(final String initialState, final TreeBuilderFactory factory) {
            final ComponentStateSupplier<String> stateSupplier = (key, ctx) -> initialState;
            final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (ctx, state) -> ctx;
            final ComponentView<String> componentView = stateUpdate -> state -> renderContext -> {
                renderContext.openNode(XmlNs.html, "span", false);
                renderContext.closeNode("span", false);
                if ("with-button".equals(state)) {
                    renderContext.openNode(XmlNs.html, "button", false);
                    renderContext.closeNode("button", false);
                }
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

        private ComponentSegment<String> createParentSegment(final ComponentSegment<String> child, final TreeBuilderFactory factory) {
            final ComponentStateSupplier<String> stateSupplier = (key, ctx) -> INITIAL_STATE;
            final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (ctx, state) -> ctx;
            final ComponentView<String> componentView = stateUpdate -> state -> renderContext -> {
                renderContext.openNode(XmlNs.html, "div", false);
                renderContext.openComponent(child);
                child.render(renderContext);
                renderContext.closeComponent();
                renderContext.closeNode("div", false);
            };

            return new ComponentSegment<>(
                    new ComponentCompositeKey(sessionId, "parentType", TreePositionPath.of("1_1")),
                    stateSupplier,
                    contextResolver,
                    componentView,
                    callbacks,
                    factory,
                    componentContext,
                    commandsEnqueue
            );
        }

        @Test
        void updates_parent_tag_children_when_child_root_nodes_change() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> child = createChildSegment("with-button", treeBuilder);
            final ComponentSegment<String> parent = createParentSegment(child, treeBuilder);

            renderSegment(treeBuilder, parent);

            final TagNode parentTag = getParentTag(child);
            final List<Node> oldChildRootNodes = getRootNodes(child);
            assertEquals(2, oldChildRootNodes.size());
            assertEquals(oldChildRootNodes.get(0), parentTag.children.get(0));
            assertEquals(oldChildRootNodes.get(1), parentTag.children.get(1));

            child.applyStateTransformation(_ -> "no-button");

            final List<Node> newChildRootNodes = getRootNodes(child);
            assertEquals(1, newChildRootNodes.size());
            assertEquals(1, parentTag.children.size());
            assertEquals(newChildRootNodes.get(0), parentTag.children.get(0));
        }

        @Test
        void throws_when_parent_no_longer_references_child_root_nodes() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> child = createChildSegment("with-button", treeBuilder);
            final ComponentSegment<String> parent = createParentSegment(child, treeBuilder);

            renderSegment(treeBuilder, parent);

            final TagNode parentTag = getParentTag(child);
            parentTag.children.clear();

            assertThrows(IllegalStateException.class, () -> child.applyStateTransformation(_ -> "no-button"));
        }

        @SuppressWarnings("unchecked")
        private List<Node> getRootNodes(final ComponentSegment<String> segment) throws Exception {
            final Field field = ComponentSegment.class.getDeclaredField("rootNodes");
            field.setAccessible(true);
            return (List<Node>) field.get(segment);
        }

        private TagNode getParentTag(final ComponentSegment<String> segment) throws Exception {
            final Field field = ComponentSegment.class.getDeclaredField("parentTag");
            field.setAccessible(true);
            return (TagNode) field.get(segment);
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

    @Nested
    public class SiblingContextIsolationTests {

        @Test
        void component_event_handler_registered_via_context_subscriber_survives_sibling_rerender() {
            final TreeBuilder treeBuilder = createTreeBuilder();

            final Subscriber[] capturedSubscriber = new Subscriber[1];
            @SuppressWarnings("unchecked")
            final ComponentSegment<String>[] childARef = new ComponentSegment[1];

            final ComponentSegmentFactory<String> childAFactory = (sid, path, tbf, ctx, cmd) ->
                    new ComponentSegment<>(
                            new ComponentCompositeKey(sid, "childA", path),
                            (key, c) -> "stateA",
                            (c, s) -> c,
                            stateUpdate -> state -> renderContext -> {
                                renderContext.openNode(XmlNs.html, "span", false);
                                renderContext.closeNode("span", false);
                            },
                            new TestCallbacks(),
                            tbf,
                            ctx,
                            cmd
                    );

            final ComponentSegmentFactory<String> childBFactory = (sid, path, tbf, ctx, cmd) -> {
                capturedSubscriber[0] = ctx.get(Subscriber.class);
                return new ComponentSegment<>(
                        new ComponentCompositeKey(sid, "childB", path),
                        (key, c) -> "stateB",
                        (c, s) -> c,
                        stateUpdate -> state -> renderContext -> {
                            renderContext.openNode(XmlNs.html, "div", false);
                            renderContext.closeNode("div", false);
                        },
                        new TestCallbacks(),
                        tbf,
                        ctx,
                        cmd
                );
            };

            final ComponentView<String> parentView = stateUpdate -> state -> renderContext -> {
                childARef[0] = renderContext.openComponent(childAFactory);
                childARef[0].render(renderContext);
                renderContext.closeComponent();

                final ComponentSegment<String> childB = renderContext.openComponent(childBFactory);
                childB.render(renderContext);
                renderContext.closeComponent();
            };

            final ComponentSegment<String> parent = new ComponentSegment<>(
                    componentId,
                    (key, ctx) -> INITIAL_STATE,
                    (ctx, s) -> ctx,
                    parentView,
                    callbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, parent);

            assertNotNull(capturedSubscriber[0], "Child B should have received a Subscriber from context");

            // Register a handler via the subscriber B received from its context
            capturedSubscriber[0].addComponentEventHandler("test.event", ctx -> {}, false);

            final long handlerCountBefore = parent.recursiveComponentEvents().stream()
                    .filter(e -> e.matches("test.event")).count();
            assertEquals(1, handlerCountBefore, "Handler should exist after registration");

            // Re-render child A — this clears A's componentEventEntries
            childARef[0].applyStateTransformation(s -> "updatedA");

            // The handler should survive because it was registered on the parent's segment, not on A's
            final long handlerCountAfter = parent.recursiveComponentEvents().stream()
                    .filter(e -> e.matches("test.event")).count();
            assertEquals(1, handlerCountAfter,
                    "Handler registered via context subscriber should survive sibling re-render");
        }
    }

    /**
     * Regression guard for the "ghost component" invariant:
     * for every ComponentCompositeKey, at most one ComponentSegment with isUnmounted=false
     * should exist; that segment must be the one currently linked into the parent's
     * children list.
     * <p>
     * The invariant was violated by an earlier equals-by-componentId check in
     * {@link ComponentSegment#applyStateTransformation} that treated a freshly-created
     * replacement segment as "still mounted" and left the old segment alive with active
     * subscriptions and DOM-write capability — see git history. The check is now identity-based,
     * so any old segment not literally carried over by reference is unmounted.
     */
    @Nested
    public class GhostComponentInvariantTests {

        private boolean isUnmounted(ComponentSegment<?> segment) throws Exception {
            Field field = ComponentSegment.class.getDeclaredField("isUnmounted");
            field.setAccessible(true);
            return (boolean) field.get(segment);
        }

        @Test
        void parent_rerender_reuses_child_segment_with_same_componentId() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();

            final List<ComponentSegment<String>> createdChildSegments = new ArrayList<>();
            final List<TestCallbacks> childCallbacksByRender = new ArrayList<>();

            // Factory mirrors Group.resolveView() pattern: each call returns a fresh
            // Component definition but with a stable componentId (same path under same parent).
            final ComponentSegmentFactory<String> childFactory = (sid, path, tbf, ctx, cmd) -> {
                final TestCallbacks cb = new TestCallbacks();
                childCallbacksByRender.add(cb);
                final ComponentSegment<String> seg = new ComponentSegment<>(
                        new ComponentCompositeKey(sid, "childType", path),
                        (key, c) -> "initial-child-state",
                        (c, s) -> c,
                        stateUpdate -> state -> renderContext -> {
                            renderContext.openNode(XmlNs.html, "span", false);
                            renderContext.closeNode("span", false);
                        },
                        cb,
                        REUSABLE_POLICY,
                        tbf,
                        ctx,
                        cmd
                );
                createdChildSegments.add(seg);
                return seg;
            };

            // Parent's view re-creates the child Definition every render, like a real Layout does.
            final ComponentView<String> parentView = stateUpdate -> state -> renderContext -> {
                renderContext.openNode(XmlNs.html, "div", false);
                final ComponentSegment<String> child = renderContext.openComponent(childFactory);
                child.render(renderContext);
                renderContext.closeComponent();
                renderContext.closeNode("div", false);
            };

            final ComponentSegment<String> parent = new ComponentSegment<>(
                    componentId,
                    (key, ctx) -> INITIAL_STATE,
                    (ctx, s) -> ctx,
                    parentView,
                    callbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, parent);
            assertEquals(1, createdChildSegments.size(), "First render creates one child segment");
            final ComponentSegment<String> oldChild = createdChildSegments.get(0);

            // Trigger a parent re-render — the equivalent of SET_PRIMARY rebuilding Scene.
            parent.applyStateTransformation(s -> NEW_STATE);

            assertEquals(2, createdChildSegments.size(),
                    "Parent re-render created a second child segment with the same componentId");
            final ComponentSegment<String> newChild = createdChildSegments.get(1);

            assertEquals(oldChild, newChild,
                    "Old and new child share the same ComponentCompositeKey (ComponentSegment.equals compares componentId)");
            assertNotSame(oldChild, newChild,
                    "But they are distinct ComponentSegment instances");

            // Invariant: at most one ComponentSegment per ComponentCompositeKey may be
            // live (isUnmounted == false). With reconciliation, the old segment is reused
            // in place and the fresh candidate is discarded before it can become live.
            assertFalse(isUnmounted(oldChild),
                    "Old child segment should remain live because it was reused in place.");
            assertTrue(isUnmounted(newChild),
                    "Fresh candidate with the same ComponentCompositeKey must be discarded, " +
                    "otherwise it would become a ghost segment racing the reused one.");
            assertSame(oldChild, parent.directChildren().get(0));
            assertFalse(childCallbacksByRender.get(0).callOrder.contains("onUnmounted:initial-child-state"),
                    "Reused child must not fire onUnmounted.");
        }

        @Test
        void discarded_candidate_cannot_apply_state_updates_after_parent_rerender() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();

            final List<ComponentSegment<String>> createdChildSegments = new ArrayList<>();
            final List<TestCallbacks> childCallbacksByRender = new ArrayList<>();

            final ComponentSegmentFactory<String> childFactory = (sid, path, tbf, ctx, cmd) -> {
                final TestCallbacks cb = new TestCallbacks();
                childCallbacksByRender.add(cb);
                final ComponentSegment<String> seg = new ComponentSegment<>(
                        new ComponentCompositeKey(sid, "childType", path),
                        (key, c) -> "initial-child-state",
                        (c, s) -> c,
                        stateUpdate -> state -> renderContext -> {
                            renderContext.openNode(XmlNs.html, "span", false);
                            renderContext.closeNode("span", false);
                        },
                        cb,
                        REUSABLE_POLICY,
                        tbf,
                        ctx,
                        cmd
                );
                createdChildSegments.add(seg);
                return seg;
            };

            final ComponentView<String> parentView = stateUpdate -> state -> renderContext -> {
                renderContext.openNode(XmlNs.html, "div", false);
                final ComponentSegment<String> child = renderContext.openComponent(childFactory);
                child.render(renderContext);
                renderContext.closeComponent();
                renderContext.closeNode("div", false);
            };

            final ComponentSegment<String> parent = new ComponentSegment<>(
                    componentId,
                    (key, ctx) -> INITIAL_STATE,
                    (ctx, s) -> ctx,
                    parentView,
                    callbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, parent);
            final ComponentSegment<String> oldChild = createdChildSegments.get(0);
            final TestCallbacks oldChildCallbacks = childCallbacksByRender.get(0);

            // Parent re-render: reuses oldChild, creates then discards a candidate at the same componentId.
            parent.applyStateTransformation(s -> NEW_STATE);
            final ComponentSegment<String> discardedCandidate = createdChildSegments.get(1);
            final TestCallbacks discardedCallbacks = childCallbacksByRender.get(1);
            discardedCallbacks.clear();

            discardedCandidate.applyStateTransformation(s -> "ghost-update");

            assertFalse(discardedCallbacks.callOrder.stream().anyMatch(s -> s.startsWith("onUpdated:")),
                    "Discarded candidate must not process state updates. " +
                    "Otherwise two segments race for the same DOM region.");
            assertFalse(isUnmounted(oldChild), "The reused original child should remain live.");
        }
    }

    /**
     * Regression guard for the subscription-handover bug.
     * <p>
     * A child segment receives a {@code Subscriber} via context — that subscriber is
     * its parent's. The child registers a handler in {@code onMounted} via
     * {@code subscriber.addComponentEventHandler(name, ...)} and stores the returned
     * registration. The child's {@code onUnmounted} unsubscribes through that exact
     * registration.
     * <p>
     * On a parent re-render, the parent clears its tables and re-renders, creating a fresh
     * child segment that registers a NEW handler under the same name. Then the OLD child
     * is unmounted; the old registration must not remove the new handler.
     * <p>
     * The fix in {@code ComponentSegment.applyStateTransformation} is to unmount old children
     * <em>before</em> clearing the parent's tables and re-rendering. This test asserts that
     * a child-registered handler is exactly preserved after a parent re-render that recreates
     * the child.
     */
    @Nested
    public class ChildSubscriptionSurvivesParentRerenderTests {

        @Test
        void child_registered_handler_survives_parent_rerender() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final String CHILD_EVENT = "child.event";

            final ComponentSegmentFactory<String> childFactory = (sid, path, tbf, ctx, cmd) -> {
                // Capture the context subscriber (this is the PARENT's subscriber).
                final Subscriber parentSubscriber = ctx.get(Subscriber.class);
                final TestCallbacks cb = new TestCallbacks() {
                    private Lookup.Registration registration;

                    @Override
                    public void onMounted(ComponentCompositeKey id, String state, StateUpdate<String> upd) {
                        super.onMounted(id, state, upd);
                        // Mirror PromptView's pattern: register on parent's subscriber.
                        registration = parentSubscriber.addComponentEventHandler(CHILD_EVENT, c -> {}, false);
                    }

                    @Override
                    public void onUnmounted(ComponentCompositeKey id, String state) {
                        super.onUnmounted(id, state);
                        registration.unsubscribe();
                    }
                };
                return new ComponentSegment<>(
                        new ComponentCompositeKey(sid, "childType", path),
                        (key, c) -> "child-state",
                        (c, s) -> c,
                        stateUpdate -> state -> rc -> {
                            rc.openNode(XmlNs.html, "span", false);
                            rc.closeNode("span", false);
                        },
                        cb,
                        tbf,
                        ctx,
                        cmd
                );
            };

            final ComponentView<String> parentView = stateUpdate -> state -> renderContext -> {
                renderContext.openNode(XmlNs.html, "div", false);
                final ComponentSegment<String> child = renderContext.openComponent(childFactory);
                child.render(renderContext);
                renderContext.closeComponent();
                renderContext.closeNode("div", false);
            };

            final ComponentSegment<String> parent = new ComponentSegment<>(
                    componentId,
                    (key, ctx) -> INITIAL_STATE,
                    (ctx, s) -> ctx,
                    parentView,
                    callbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, parent);

            // After the initial render, exactly one handler exists for CHILD_EVENT.
            assertEquals(1L,
                    parent.recursiveComponentEvents().stream().filter(e -> e.matches(CHILD_EVENT)).count(),
                    "child registered exactly one handler on initial mount");

            // Trigger a parent re-render. This unmounts the old child, re-creates a fresh child,
            // which re-registers its handler. With the fix in place, the old registration
            // removes only the old entry, and the new registration remains.
            parent.applyStateTransformation(s -> NEW_STATE);

            assertEquals(1L,
                    parent.recursiveComponentEvents().stream().filter(e -> e.matches(CHILD_EVENT)).count(),
                    "After parent re-render, exactly one CHILD_EVENT handler must remain - " +
                    "the freshly-registered one from the new child. If this is 0, the old child's " +
                    "onUnmounted removed the new entry.");
        }
    }

    @Nested
    public class ExactEventRegistrationTests {

        @Test
        void registration_unsubscribe_removes_exact_same_name_handler() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final Subscriber[] capturedSubscriber = new Subscriber[1];
            final ComponentCallbacks<String> capturingCallbacks = new TestCallbacks() {
                @Override
                public void onAfterRendered(String state,
                                            Subscriber subscriber,
                                            CommandsEnqueue commandsEnqueue,
                                            StateUpdate<String> stateUpdate) {
                    super.onAfterRendered(state, subscriber, commandsEnqueue, stateUpdate);
                    capturedSubscriber[0] = subscriber;
                }
            };
            final ComponentSegment<String> segment = new ComponentSegment<>(
                    componentId,
                    (key, ctx) -> INITIAL_STATE,
                    (ctx, s) -> ctx,
                    stateUpdate -> state -> rc -> {
                        rc.openNode(XmlNs.html, "div", false);
                        rc.closeNode("div", false);
                    },
                    capturingCallbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, segment);
            final AtomicInteger firstCalls = new AtomicInteger();
            final AtomicInteger secondCalls = new AtomicInteger();

            final Lookup.Registration first = capturedSubscriber[0].addComponentEventHandler(
                    "same.event", _ -> firstCalls.incrementAndGet(), false);
            capturedSubscriber[0].addComponentEventHandler(
                    "same.event", _ -> secondCalls.incrementAndGet(), false);

            first.unsubscribe();
            segment.recursiveComponentEvents().stream()
                    .filter(entry -> entry.matches("same.event"))
                    .forEach(entry -> entry.eventHandler().accept(
                            new ComponentEventEntry.EventContext("same.event", Map.of())));

            assertEquals(0, firstCalls.get());
            assertEquals(1, secondCalls.get());
        }
    }

    @Nested
    public class ReconciliationTests {

        private boolean isUnmounted(ComponentSegment<?> segment) throws Exception {
            Field field = ComponentSegment.class.getDeclaredField("isUnmounted");
            field.setAccessible(true);
            return (boolean) field.get(segment);
        }

        @Test
        void parent_rerender_reuses_same_type_child_by_position() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final List<ComponentSegment<String>> createdChildSegments = new ArrayList<>();
            final List<TestCallbacks> childCallbacks = new ArrayList<>();

            final ComponentSegmentFactory<String> childFactory = (sid, path, tbf, ctx, cmd) -> {
                final TestCallbacks cb = new TestCallbacks();
                childCallbacks.add(cb);
                final ComponentSegment<String> child = new ComponentSegment<>(
                        new ComponentCompositeKey(sid, "reusedChild", path),
                        (key, c) -> "child-initial",
                        (c, s) -> c,
                        stateUpdate -> state -> rc -> {
                            rc.openNode(XmlNs.html, "span", false);
                            rc.addTextNode(state);
                            rc.closeNode("span", false);
                        },
                        cb,
                        REUSABLE_POLICY,
                        tbf,
                        ctx,
                        cmd
                );
                createdChildSegments.add(child);
                return child;
            };

            final ComponentView<String> parentView = stateUpdate -> state -> rc -> {
                rc.openNode(XmlNs.html, "div", false);
                final ComponentSegment<String> child = rc.openComponent(childFactory);
                child.render(rc);
                rc.closeComponent();
                rc.closeNode("div", false);
            };

            final ComponentSegment<String> parent = new ComponentSegment<>(
                    componentId,
                    (key, ctx) -> INITIAL_STATE,
                    (ctx, s) -> ctx.with(new ContextKey.StringKey<>("reconcile.state", String.class), s),
                    parentView,
                    callbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, parent);
            final ComponentSegment<String> reused = createdChildSegments.get(0);
            reused.setState("child-state-survives");
            capturedCommands.clear();

            parent.applyStateTransformation(s -> NEW_STATE);

            assertEquals(2, createdChildSegments.size(),
                    "A candidate segment is still built from the new component definition");
            assertSame(reused, parent.directChildren().get(0),
                    "The old child segment should be kept as the live child");
            assertFalse(isUnmounted(reused), "The reused child must remain mounted");
            assertFalse(childCallbacks.get(0).callOrder.stream().anyMatch(s -> s.startsWith("onUnmounted")),
                    "Reusing a child must not fire onUnmounted");

            reused.applyStateTransformation(s -> s + "-updated");
            assertFalse(childCallbacks.get(0).callOrder.stream().anyMatch("onMounted:child-state-survives-updated"::equals),
                    "A reused child update must not remount the segment");
        }

        @Test
        void reused_child_runs_after_rendered_again_without_remounting() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final AtomicInteger afterRenderedCalls = new AtomicInteger();
            final AtomicInteger mountedCalls = new AtomicInteger();

            final ComponentSegmentFactory<String> childFactory = (sid, path, tbf, ctx, cmd) ->
                    new ComponentSegment<>(
                            new ComponentCompositeKey(sid, "reusedLifecycleChild", path),
                            (key, c) -> "child",
                            (c, s) -> c,
                            stateUpdate -> state -> rc -> {
                                rc.openNode(XmlNs.html, "span", false);
                                rc.closeNode("span", false);
                            },
                            new TestCallbacks() {
                                @Override
                                public void onAfterRendered(String state,
                                                            Subscriber subscriber,
                                                            CommandsEnqueue commandsEnqueue,
                                                            StateUpdate<String> stateUpdate) {
                                    afterRenderedCalls.incrementAndGet();
                                }

                                @Override
                                public void onMounted(ComponentCompositeKey componentId,
                                                      String state,
                                                      StateUpdate<String> stateUpdate) {
                                    mountedCalls.incrementAndGet();
                                }
                            },
                            REUSABLE_POLICY,
                            tbf,
                            ctx,
                            cmd
                    );

            final ComponentView<String> parentView = stateUpdate -> state -> rc -> {
                final ComponentSegment<String> child = rc.openComponent(childFactory);
                child.render(rc);
                rc.closeComponent();
            };

            final ComponentSegment<String> parent = new ComponentSegment<>(
                    componentId,
                    (key, ctx) -> INITIAL_STATE,
                    (ctx, s) -> ctx,
                    parentView,
                    callbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, parent);
            parent.applyStateTransformation(s -> NEW_STATE);

            assertEquals(2, afterRenderedCalls.get(),
                    "A reused child still rendered twice and must refresh after-render subscriptions");
            assertEquals(1, mountedCalls.get(),
                    "Reconciliation must not remount the reused child");
        }

        @Test
        void non_reusable_child_is_recreated_on_parent_rerender() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final List<ComponentSegment<String>> createdChildSegments = new ArrayList<>();
            final List<TestCallbacks> childCallbacks = new ArrayList<>();

            final ComponentSegmentFactory<String> childFactory = (sid, path, tbf, ctx, cmd) -> {
                final TestCallbacks cb = new TestCallbacks();
                childCallbacks.add(cb);
                final ComponentSegment<String> child = new ComponentSegment<>(
                        new ComponentCompositeKey(sid, "nonReusableChild", path),
                        (key, c) -> "child-initial",
                        (c, s) -> c,
                        stateUpdate -> state -> rc -> {
                            rc.openNode(XmlNs.html, "span", false);
                            rc.closeNode("span", false);
                        },
                        cb,
                        new ComponentRuntimePolicy() {
                            @Override
                            public boolean isReusable() {
                                return false;
                            }
                        },
                        tbf,
                        ctx,
                        cmd
                );
                createdChildSegments.add(child);
                return child;
            };

            final ComponentView<String> parentView = stateUpdate -> state -> rc -> {
                final ComponentSegment<String> child = rc.openComponent(childFactory);
                child.render(rc);
                rc.closeComponent();
            };

            final ComponentSegment<String> parent = new ComponentSegment<>(
                    componentId,
                    (key, ctx) -> INITIAL_STATE,
                    (ctx, s) -> ctx,
                    parentView,
                    callbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, parent);
            final ComponentSegment<String> oldChild = createdChildSegments.get(0);

            parent.applyStateTransformation(s -> NEW_STATE);

            assertEquals(2, createdChildSegments.size());
            assertSame(createdChildSegments.get(1), parent.directChildren().get(0));
            assertTrue(isUnmounted(oldChild), "Opting out of reuse should keep recreate semantics");
            assertTrue(childCallbacks.get(0).callOrder.contains("onUnmounted:child-initial"));
        }

        @Test
        void reused_child_scope_observes_context_replacement_from_parent_rerender() {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ContextKey.StringKey<String> key =
                    new ContextKey.StringKey<>("reconcile.context.value", String.class);
            final AtomicReference<String> observed = new AtomicReference<>();

            final ComponentSegmentFactory<String> childFactory = (sid, path, tbf, ctx, cmd) -> {
                final ComponentSegment<String> child = new ComponentSegment<>(
                        new ComponentCompositeKey(sid, "contextChild", path),
                        (componentKey, c) -> "child",
                        (c, s) -> c,
                        stateUpdate -> state -> rc -> {
                            rc.openNode(XmlNs.html, "span", false);
                            rc.closeNode("span", false);
                        },
                        new TestCallbacks(),
                        REUSABLE_POLICY,
                        tbf,
                        ctx,
                        cmd
                );
                new ContextLookup(child.contextScope(), cmd, new NoOpSubscriber())
                        .watch(key, observed::set);
                return child;
            };

            final ComponentView<String> parentView = stateUpdate -> state -> rc -> {
                final ComponentSegment<String> child = rc.openComponent(childFactory);
                child.render(rc);
                rc.closeComponent();
            };

            final ComponentSegment<String> parent = new ComponentSegment<>(
                    componentId,
                    (componentKey, ctx) -> "one",
                    (ctx, state) -> ctx.with(key, state),
                    parentView,
                    callbacks,
                    treeBuilder,
                    componentContext,
                    commandsEnqueue
            );

            renderSegment(treeBuilder, parent);
            parent.applyStateTransformation(_ -> "two");

            assertEquals("two", observed.get());
            assertEquals("two", parent.directChildren().get(0).componentContext().get(key));
        }
    }

    /**
     * Regression tests for the lost-handler-on-parent-re-render bug.
     *
     * <p>Post-fix, {@code addComponentEventHandler} returns a reference-based
     * {@link Lookup.Registration} that removes only the specific entry it created.</p>
     */
    @Nested
    public class EventHandlerRegistrationTests {

        private List<ComponentEventEntry> entries(final ComponentSegment<?> segment) throws Exception {
            final Field f = ComponentSegment.class.getDeclaredField("componentEventEntries");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            final List<ComponentEventEntry> list = (List<ComponentEventEntry>) f.get(segment);
            return list;
        }

        @Test
        void registration_unsubscribe_removes_only_its_own_entry() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegment(treeBuilder);

            final Lookup.Registration reg1 = segment.addComponentEventHandler(
                    "foo.event", ctx -> {}, false);
            segment.addComponentEventHandler("foo.event", ctx -> {}, false);

            assertEquals(2, entries(segment).size(),
                    "Both handlers should be registered for the same event name");

            reg1.unsubscribe();

            assertEquals(1, entries(segment).size(),
                    "Only the registration's own entry must be removed");
        }

        @Test
        void registration_unsubscribe_after_event_table_refresh_is_safe_noop() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegment(treeBuilder);

            final Lookup.Registration regA = segment.addComponentEventHandler(
                    "prompt.newMessage", ctx -> {}, false);

            final Field f = ComponentSegment.class.getDeclaredField("componentEventEntries");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            final List<ComponentEventEntry> list = (List<ComponentEventEntry>) f.get(segment);
            list.clear();

            segment.addComponentEventHandler("prompt.newMessage", ctx -> {}, false);
            assertEquals(1, entries(segment).size(), "entry-B is the sole entry");

            regA.unsubscribe();

            assertEquals(1, entries(segment).size(),
                    "regA.unsubscribe() must not remove a later sibling registration");
        }

        @Test
        void typed_addEventHandler_returns_registration_and_removes_only_its_entry() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegment(treeBuilder);

            final EventKey.SimpleKey<String> key =
                    new EventKey.SimpleKey<>("typed.event", String.class);

            final Lookup.Registration reg1 = segment.addEventHandler(
                    key, (name, payload) -> {});
            segment.addEventHandler(key, (name, payload) -> {});

            assertEquals(2, entries(segment).size());
            reg1.unsubscribe();
            assertEquals(1, entries(segment).size(),
                    "Typed addEventHandler must also expose reference-based removal");
        }

        @Test
        void void_addEventHandler_returns_registration_and_removes_only_its_entry() throws Exception {
            final TreeBuilder treeBuilder = createTreeBuilder();
            final ComponentSegment<String> segment = createSegment(treeBuilder);

            final EventKey.VoidKey key = new EventKey.VoidKey("void.event");

            final Lookup.Registration reg1 = segment.addEventHandler(key, () -> {});
            segment.addEventHandler(key, () -> {});

            assertEquals(2, entries(segment).size());
            reg1.unsubscribe();
            assertEquals(1, entries(segment).size(),
                    "Void-key addEventHandler must also expose reference-based removal");
        }
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override public void addWindowEventHandler(String type, java.util.function.Consumer<rsp.page.EventContext> h,
                                                    boolean preventDefault, rsp.dom.DomEventEntry.Modifier mod) {}
        @Override public Lookup.Registration addComponentEventHandler(String type,
                                                                      java.util.function.Consumer<ComponentEventEntry.EventContext> h,
                                                                      boolean preventDefault) {
            return () -> {};
        }
    }
}
