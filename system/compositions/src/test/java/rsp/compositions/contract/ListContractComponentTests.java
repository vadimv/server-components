package rsp.compositions.contract;

import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.GenericTaskEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.dsl.Html.div;

class ListContractComponentTests {

    @Test
    void page_intent_reloads_the_component_owned_cache() {
        TestListContract contract = new TestListContract();
        Parent parent = new Parent(contract, new ParentState("1", "asc"));
        Harness harness = render(parent);

        listSegment(harness.root()).dispatch(new ListView.PageRequested(2));
        harness.commands().runTasks();

        assertEquals(2, contract.lastUpdatedState.page());
        assertEquals("page-2", contract.lastUpdatedState.rows().getFirst().get("id"));
    }

    @Test
    void query_context_changes_refresh_the_reused_component_cache() {
        TestListContract contract = new TestListContract();
        Parent parent = new Parent(contract, new ParentState("1", "asc"));
        Harness harness = render(parent);

        harness.root().dispatch(new ParentState("2", "desc"));
        harness.commands().runTasks();

        assertEquals(2, contract.lastUpdatedState.page());
        assertEquals("desc", contract.lastUpdatedState.sort());
        assertEquals("page-2", contract.lastUpdatedState.rows().getFirst().get("id"));
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
        private final TestListContract contract;
        private final ParentState initialState;

        private Parent(TestListContract contract, ParentState initialState) {
            this.contract = contract;
            this.initialState = initialState;
        }

        @Override
        public ComponentStateSupplier<ParentState> initStateSupplier() {
            return (_, _) -> initialState;
        }

        @Override
        public ComponentView<ParentState, ParentState> componentView() {
            return _ -> _ -> contract;
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

    private static final class TestListContract extends ListContractComponent<TestItem> {
        private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
        private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

        private ListView.ListViewState lastUpdatedState;

        private TestListContract() {
            super(_ -> _ -> div());
        }

        @Override
        protected QueryParam<Integer> pageQueryParam() {
            return PAGE;
        }

        @Override
        protected String sort(rsp.component.Lookup lookup) {
            return SORT.resolve(lookup);
        }

        @Override
        protected List<TestItem> items(int page, int pageSize, String sort) {
            return List.of(new TestItem("page-" + page, sort));
        }

        @Override
        protected Class<? extends Contract> createElementContract() {
            return TestListContract.class;
        }

        @Override
        protected Class<? extends Contract> editElementContract() {
            return TestListContract.class;
        }

        @Override
        public String title() {
            return "Items";
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
