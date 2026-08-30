package rsp.compositions.block;

import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.compositions.schema.DataSchema;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.GenericTaskEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.dsl.Html.div;

class ListBlockTests {

    @Test
    void page_intent_reloads_the_component_owned_cache() {
        TestListBlock block = new TestListBlock();
        Parent parent = new Parent(block, new ParentState("1", "asc"));
        Harness harness = render(parent);

        listSegment(harness.root()).dispatch(new ListView.PageRequested(2));
        harness.commands().runTasks();

        assertEquals(2, block.lastUpdatedState.page());
        assertEquals("page-2", block.lastUpdatedState.rows().getFirst().get("id"));
    }

    @Test
    void query_context_changes_refresh_the_reused_component_cache() {
        TestListBlock block = new TestListBlock();
        Parent parent = new Parent(block, new ParentState("1", "asc"));
        Harness harness = render(parent);

        harness.root().dispatch(new ParentState("2", "desc"));
        harness.commands().runTasks();

        assertEquals(2, block.lastUpdatedState.page());
        assertEquals("desc", block.lastUpdatedState.sort());
        assertEquals("page-2", block.lastUpdatedState.rows().getFirst().get("id"));
    }

    @Test
    void initial_out_of_range_page_is_clamped_using_the_exact_total() {
        TestListBlock block = new TestListBlock();

        render(new Parent(block, new ParentState("99", "asc")));

        assertEquals(3, block.initialState.page());
        assertEquals(30, block.initialState.totalItems());
        assertEquals("page-3", block.initialState.rows().getFirst().get("id"));
    }

    @Test
    void criteria_and_page_size_changes_reset_the_page_and_clear_selection() {
        TestListBlock block = new TestListBlock();
        Harness harness = render(new Parent(block, new ParentState("2", "asc")));
        ComponentSegment<ListView.ListViewState> segment = listSegment(harness.root());

        segment.dispatch(new ListView.SelectionChanged(java.util.Set.of("page-2")));
        harness.commands().runTasks();
        segment.dispatch(new ListView.QueryRequested(" needle ", java.util.Map.of("unknown", "ignored")));
        harness.commands().runTasks();

        assertEquals(1, block.lastUpdatedState.page());
        assertEquals("needle", block.lastUpdatedState.query().search());
        assertTrue(block.lastUpdatedState.query().filters().isEmpty());
        assertTrue(block.lastUpdatedState.selectedIds().isEmpty());

        segment.dispatch(new ListView.PageSizeRequested(25));
        harness.commands().runTasks();
        assertEquals(25, block.lastUpdatedState.pageSize());
        assertEquals(1, block.lastUpdatedState.page());
    }

    @SuppressWarnings("unchecked")
    private static ComponentSegment<ListView.ListViewState> listSegment(ComponentSegment<ParentState> root) {
        return (ComponentSegment<ListView.ListViewState>) root.directChildren().getFirst();
    }

    private static Harness render(Parent parent) {
        RecordingCommands commands = new RecordingCommands();
        TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                new ComponentContext().with(CommandsEnqueue.class, commands),
                commands);
        ComponentSegment<ParentState> root = treeBuilder.openComponent(parent);
        root.render(treeBuilder);
        treeBuilder.closeComponent();
        assertTrue(treeBuilder.exceptions().isEmpty(), () -> treeBuilder.exceptions().toString());
        return new Harness(root, commands);
    }

    private record Harness(ComponentSegment<ParentState> root, RecordingCommands commands) {
    }

    private record ParentState(String page, String sort) {
    }

    private static final class Parent extends Component<ParentState, ParentState> {
        private final TestListBlock block;
        private final ParentState initialState;

        private Parent(TestListBlock block, ParentState initialState) {
            this.block = block;
            this.initialState = initialState;
        }

        @Override
        public ComponentStateSupplier<ParentState> initStateSupplier() {
            return (_, _) -> initialState;
        }

        @Override
        public ComponentView<ParentState, ParentState> componentView() {
            return _ -> _ -> block;
        }

        @Override
        public java.util.function.BiFunction<ComponentContext, ParentState, ComponentContext> subComponentsContext() {
            return (context, state) -> context
                    .with(ContextKeys.URL_QUERY.with("p"), state.page())
                    .with(ContextKeys.URL_QUERY.with("sort"), state.sort())
                    .with(ContextKeys.ROUTE_PATH, "/items");
        }

        @Override
        protected void onIntent(ParentState intent, ParentState state, StateUpdater<ParentState> stateUpdater) {
            stateUpdater.setState(intent);
        }
    }

    private static final class TestListBlock extends ListBlock<TestItem> {
        private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
        private static final DataSchema SCHEMA = DataSchema.fromRecordClass(TestItem.class);

        private ListView.ListViewState lastUpdatedState;
        private ListView.ListViewState initialState;

        private TestListBlock() {
            super(_ -> _ -> div());
        }

        @Override
        protected QueryParam<Integer> pageQueryParam() {
            return PAGE;
        }

        @Override
        protected DataSchema listSchema() {
            return SCHEMA;
        }

        @Override
        protected ListPage<TestItem> items(ListQuery query) {
            if (query.page() > 3) {
                return new ListPage<>(List.of(), 30);
            }
            return new ListPage<>(List.of(new TestItem("page-" + query.page(),
                    query.sort().direction().queryValue())), 30);
        }

        @Override
        protected Class<? extends Block<?, ?>> createElementBlock() {
            return TestListBlock.class;
        }

        @Override
        protected Class<? extends Block<?, ?>> editElementBlock() {
            return TestListBlock.class;
        }

        @Override
        public String title() {
            return "Items";
        }

        @Override
        protected void onBlockMounted(ListView.ListViewState state,
                                      StateUpdater<ListView.ListViewState> stateUpdater) {
            initialState = state;
            super.onBlockMounted(state, stateUpdater);
        }

        @Override
        public void onUpdated(rsp.component.ComponentCompositeKey componentId,
                              ListView.ListViewState oldState,
                              ListView.ListViewState newState,
                              StateUpdater<ListView.ListViewState> stateUpdater) {
            lastUpdatedState = newState;
        }
    }

    public record TestItem(String id, String sort) {
    }

    private static final class RecordingCommands implements CommandsEnqueue {
        private final List<Command> commands = new ArrayList<>();
        private int nextCommand;

        @Override
        public void offer(Command command) {
            commands.add(command);
        }

        private void runTasks() {
            while (nextCommand < commands.size()) {
                Command command = commands.get(nextCommand++);
                if (command instanceof GenericTaskEvent taskEvent) {
                    taskEvent.task().run();
                }
            }
        }
    }
}
