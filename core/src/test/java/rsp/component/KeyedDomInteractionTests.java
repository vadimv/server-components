package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.component.definitions.Component;
import rsp.dom.NodeId;
import rsp.dom.TreePositionPath;
import rsp.dsl.Definition;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.RemoteCommand;
import rsp.ref.ElementRef;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.dsl.Html.*;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;

class KeyedDomInteractionTests {

    private final QualifiedSessionId sessionId = new QualifiedSessionId("device", "session");
    private final ComponentContext componentContext = new ComponentContext();
    private final List<Command> commands = new ArrayList<>();
    private final CommandsEnqueue commandsEnqueue = commands::add;

    @Test
    void keyed_dom_event_uses_key_aware_node_id_when_key_is_before_event() {
        final TreeBuilder treeBuilder = render(state ->
                div(button(key("save"),
                           on("click", _ -> {}),
                           text("Save"))));

        assertEquals(NodeId.of("1_kssave"), treeBuilder.recursiveEvents().getFirst().eventTarget.nodeId());
    }

    @Test
    void keyed_dom_event_uses_key_aware_node_id_when_key_is_after_event() {
        final TreeBuilder treeBuilder = render(state ->
                div(button(on("click", _ -> {}),
                           key("save"),
                           text("Save"))));

        assertEquals(NodeId.of("1_kssave"), treeBuilder.recursiveEvents().getFirst().eventTarget.nodeId());
    }

    @Test
    void keyed_nested_dom_event_uses_key_aware_node_id() {
        final TreeBuilder treeBuilder = render(state ->
                ul(li(key(42),
                      button(on("click", _ -> {}),
                             text("Open")))));

        assertEquals(NodeId.of("1_kn42_1"), treeBuilder.recursiveEvents().getFirst().eventTarget.nodeId());
    }

    @Test
    void keyed_ref_uses_key_aware_node_id() {
        final ElementRef inputRef = createElementRef();
        final TreeBuilder treeBuilder = render(state ->
                div(input(key(7),
                          ref(inputRef),
                          attr("type", "text"))));

        assertEquals(NodeId.of("1_kn7"), treeBuilder.recursiveRefs().get(inputRef));
    }

    @Test
    void keyed_dom_rejects_child_components() {
        final TreeBuilder treeBuilder = render(state ->
                div(key(1),
                    new StaticChildComponent()));

        assertEquals(1, treeBuilder.exceptions().size());
        assertInstanceOf(IllegalStateException.class, treeBuilder.exceptions().getFirst());
        assertTrue(treeBuilder.exceptions().getFirst().getMessage().contains("components inside keyed DOM"));
    }

    @Test
    void keyed_dom_rejects_child_components_when_key_is_after_component() {
        final TreeBuilder treeBuilder = render(state ->
                div(new StaticChildComponent(),
                    key(1)));

        assertEquals(1, treeBuilder.exceptions().size());
        assertInstanceOf(IllegalStateException.class, treeBuilder.exceptions().getFirst());
        assertTrue(treeBuilder.exceptions().getFirst().getMessage().contains("components inside keyed DOM"));
    }

    @Test
    void invalid_keyed_update_restores_previous_state_tree_and_sends_no_dom_patch() {
        final ComponentSegment<List<Long>> segment = createListSegment(List.of(1L, 2L));
        final TreeBuilder treeBuilder = new TreeBuilder(sessionId, TreePositionPath.of("1"), componentContext, commandsEnqueue);
        treeBuilder.openComponent(segment);
        segment.render(treeBuilder);
        treeBuilder.closeComponent();
        commands.clear();

        assertThrows(IllegalStateException.class, () -> segment.setState(List.of(1L, 1L)));

        assertTrue(commands.stream().noneMatch(RemoteCommand.ModifyDom.class::isInstance));
        assertHtmlFragmentsEqual("""
                <ul>
                 <li data-rsp-key="kn1">item-1</li>
                 <li data-rsp-key="kn2">item-2</li>
                </ul>""", treeBuilder.html());
    }

    @Test
    void keyed_event_is_relistened_when_same_key_node_is_recreated() {
        final ComponentSegment<String> segment = createSegment("button", state ->
                div("button".equals(state)
                        ? button(key("save"), on("click", _ -> {}), text("Save"))
                        : a(key("save"), on("click", _ -> {}), text("Save"))));
        final TreeBuilder treeBuilder = new TreeBuilder(sessionId, TreePositionPath.of("1"), componentContext, commandsEnqueue);
        treeBuilder.openComponent(segment);
        segment.render(treeBuilder);
        treeBuilder.closeComponent();
        commands.clear();

        segment.setState("link");

        final List<RemoteCommand.ListenEvent> listenCommands = commands.stream()
                .filter(RemoteCommand.ListenEvent.class::isInstance)
                .map(RemoteCommand.ListenEvent.class::cast)
                .toList();
        assertEquals(1, listenCommands.size(), "a recreated keyed node needs a fresh DOM listener");
        assertEquals(NodeId.of("1_kssave"), listenCommands.getFirst().events().getFirst().eventTarget.nodeId());
    }

    private TreeBuilder render(final java.util.function.Function<String, Definition> viewDefinition) {
        final ComponentSegment<String> segment = createSegment("state", viewDefinition);
        final TreeBuilder treeBuilder = new TreeBuilder(sessionId, TreePositionPath.of("1"), componentContext, commandsEnqueue);
        treeBuilder.openComponent(segment);
        segment.render(treeBuilder);
        treeBuilder.closeComponent();
        return treeBuilder;
    }

    private ComponentSegment<String> createSegment(final String initialState,
                                                   final java.util.function.Function<String, Definition> viewDefinition) {
        final ComponentStateSupplier<String> stateSupplier = (_, _) -> initialState;
        final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (ctx, _) -> ctx;
        final ComponentView<String> componentView = _ -> state -> viewDefinition.apply(state);
        return new ComponentSegment<>(new ComponentCompositeKey(sessionId, "test", TreePositionPath.of("1")),
                                      stateSupplier,
                                      contextResolver,
                                      componentView,
                                      new NoOpCallbacks<>(),
                                      new TreeBuilder(sessionId, TreePositionPath.of("1"), componentContext, commandsEnqueue),
                                      componentContext,
                                      commandsEnqueue);
    }

    private ComponentSegment<List<Long>> createListSegment(final List<Long> initialState) {
        final ComponentStateSupplier<List<Long>> stateSupplier = (_, _) -> initialState;
        final BiFunction<ComponentContext, List<Long>, ComponentContext> contextResolver = (ctx, _) -> ctx;
        final ComponentView<List<Long>> componentView = _ -> state ->
                ul(of(state.stream().map(id -> li(key(id), text("item-" + id)))));
        return new ComponentSegment<>(new ComponentCompositeKey(sessionId, "list", TreePositionPath.of("1")),
                                      stateSupplier,
                                      contextResolver,
                                      componentView,
                                      new NoOpCallbacks<>(),
                                      new TreeBuilder(sessionId, TreePositionPath.of("1"), componentContext, commandsEnqueue),
                                      componentContext,
                                      commandsEnqueue);
    }

    private static final class NoOpCallbacks<S> implements ComponentCallbacks<S> {
        @Override
        public boolean onBeforeUpdated(final S newState, final CommandsEnqueue commandsEnqueue) {
            return true;
        }

        @Override
        public void onAfterRendered(final S state,
                                    final Subscriber subscriber,
                                    final CommandsEnqueue commandsEnqueue,
                                    final StateUpdate<S> stateUpdate) {
        }

        @Override
        public void onMounted(final ComponentCompositeKey componentId, final S state, final StateUpdate<S> stateUpdate) {
        }

        @Override
        public void onUpdated(final ComponentCompositeKey componentId,
                              final S oldState,
                              final S newState,
                              final StateUpdate<S> stateUpdate) {
        }

        @Override
        public void onUnmounted(final ComponentCompositeKey componentId, final S state) {
        }
    }

    private static final class StaticChildComponent extends Component<String> {
        @Override
        public ComponentStateSupplier<String> initStateSupplier() {
            return (_, _) -> "child";
        }

        @Override
        public ComponentView<String> componentView() {
            return _ -> state -> span(text(state));
        }
    }
}
