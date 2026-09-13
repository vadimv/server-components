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
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.GenericTaskEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.dsl.Html.div;

class ListBlockTests {

    private static final String DIAGNOSTIC_CANARY = "diagnostic-canary-secret";

    private static final Composition TEST_COMPOSITION = new Composition(
            new Router().route("/related", TestListBlock.class),
            new DefaultLayout(),
            new Group().bind(TestListBlock.class, TestListBlock::new));

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

    @Test
    void confirmed_delete_exposes_busy_state_and_suppresses_duplicate_requests() {
        TestListBlock block = new TestListBlock();
        Harness harness = render(new Parent(block, new ParentState("1", "asc")));
        ComponentSegment<ListView.ListViewState> segment = listSegment(harness.root());

        segment.dispatch(new ListView.DeleteConfirmed("page-1"));
        segment.dispatch(new ListView.DeleteConfirmed("page-1"));
        harness.commands().runNextTask();
        harness.commands().runNextTask();
        harness.commands().runNextTask();

        assertEquals(ListStatus.DELETING, block.lastUpdatedState.status());
        assertEquals("Deleting 1 item…", block.lastUpdatedState.message());
        assertEquals(0, block.bulkDeleteCalls);

        harness.commands().runTasks();

        assertEquals(1, block.bulkDeleteCalls);
        assertEquals(Set.of("page-1"), block.lastDeletedIds);
        assertEquals(ListStatus.READY, block.lastUpdatedState.status());
        assertEquals("1 item deleted.", block.lastUpdatedState.message());
    }

    @Test
    void agent_actions_match_configured_crud_capabilities() {
        TestListBlock readOnly = new TestListBlock(false, false, false);
        List<String> names = readOnly.agentActions().stream().map(BlockAction::action).toList();

        assertFalse(names.contains("create"));
        assertFalse(names.contains("edit"));
        assertFalse(names.contains("edit_selected"));
        assertFalse(names.contains("delete"));
        assertFalse(names.contains("delete_selected"));
        assertTrue(names.contains("page"));
        assertTrue(names.contains("select_all"));
    }

    @Test
    void related_list_specs_are_route_resolved_into_list_state() {
        TestListBlock block = new TestListBlock(true, true, true, true);

        render(new Parent(block, new ParentState("1", "asc")));

        assertEquals(1, block.initialState.relatedListColumns().size());
        ListView.RelatedListColumn related = block.initialState.relatedListColumns().getFirst();
        assertEquals("/related", related.targetPath());
        assertEquals("id", related.sourceField());
        assertEquals("ownerId", related.filterField());
    }

    @Test
    void load_failure_does_not_expose_exception_details() {
        TestListBlock block = new TestListBlock();
        block.failLoads = true;

        render(new Parent(block, new ParentState("1", "asc")));

        assertEquals("Could not load items.", block.initialState.message());
        assertFalse(block.initialState.message().contains(DIAGNOSTIC_CANARY));
    }

    @Test
    void delete_failure_does_not_expose_exception_details() {
        TestListBlock block = new TestListBlock();
        Harness harness = render(new Parent(block, new ParentState("1", "asc")));
        block.failDeletes = true;

        listSegment(harness.root()).dispatch(new ListView.DeleteConfirmed("page-1"));
        harness.commands().runTasks();

        assertEquals("Delete failed.", block.lastUpdatedState.message());
        assertFalse(block.lastUpdatedState.message().contains(DIAGNOSTIC_CANARY));
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
                    .with(ContextKeys.ROUTE_PATH, "/items")
                    .with(ContextKeys.ROUTE_COMPOSITION, TEST_COMPOSITION);
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
        private final boolean createAllowed;
        private final boolean editAllowed;
        private final boolean deleteAllowed;
        private final boolean related;
        private int bulkDeleteCalls;
        private Set<String> lastDeletedIds = Set.of();
        private boolean failLoads;
        private boolean failDeletes;

        private TestListBlock() {
            this(true, true, true, false);
        }

        private TestListBlock(boolean createAllowed, boolean editAllowed, boolean deleteAllowed) {
            this(createAllowed, editAllowed, deleteAllowed, false);
        }

        private TestListBlock(boolean createAllowed, boolean editAllowed, boolean deleteAllowed, boolean related) {
            super(_ -> _ -> div());
            this.createAllowed = createAllowed;
            this.editAllowed = editAllowed;
            this.deleteAllowed = deleteAllowed;
            this.related = related;
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
            if (failLoads) {
                throw new RuntimeException(DIAGNOSTIC_CANARY);
            }
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
        protected List<RelatedListLinkSpec> relatedListLinks() {
            return related
                    ? List.of(RelatedListLinkSpec.to("related", "Related", "id",
                            TestListBlock.class, "ownerId", "View related"))
                    : List.of();
        }

        @Override
        protected boolean canCreate() {
            return createAllowed;
        }

        @Override
        protected boolean canEdit() {
            return editAllowed;
        }

        @Override
        protected boolean canDelete() {
            return deleteAllowed;
        }

        @Override
        protected DeleteResult bulkDelete(Set<String> ids) {
            if (failDeletes) {
                throw new RuntimeException(DIAGNOSTIC_CANARY);
            }
            bulkDeleteCalls++;
            lastDeletedIds = Set.copyOf(ids);
            return DeleteResult.allDeleted(ids);
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

        private void runNextTask() {
            while (nextCommand < commands.size()) {
                Command command = commands.get(nextCommand++);
                if (command instanceof GenericTaskEvent taskEvent) {
                    taskEvent.task().run();
                    return;
                }
            }
            throw new AssertionError("No queued task available");
        }
    }
}
