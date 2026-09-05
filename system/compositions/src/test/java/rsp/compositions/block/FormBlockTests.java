package rsp.compositions.block;

import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.component.StateUpdater;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldType;
import rsp.compositions.ui.EditView;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.GenericTaskEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.dsl.Html.div;

class FormBlockTests {

    @Test
    void create_initialization_applies_defaults_and_keeps_nullable_fields() {
        TestCreateBlock block = new TestCreateBlock();
        render(block, "/items/new", Map.of());

        assertEquals(FormMode.CREATE, block.initialState.mode());
        assertEquals(5, block.initialState.fieldValues().get("count"));
        assertEquals("server", block.initialState.fieldValues().get("locked"));
        assertTrue(block.initialState.fieldValues().containsKey("date"));
        assertNull(block.initialState.fieldValues().get("date"));
        assertFalse(block.initialState.isDirty());
    }

    @Test
    void submission_converts_values_validates_and_ignores_untrusted_fields() {
        TestCreateBlock block = new TestCreateBlock();
        Harness harness = render(block, "/items/new", Map.of());

        formSegment(harness.root()).dispatch(new EditView.FormValuesCollected(Map.of(
                "id", "hacked", "name", "", "count", "not-a-number",
                "locked", "hacked", "unknown", "ignored")));
        harness.commands().runTasks();

        EditView.EditViewState state = block.lastState;
        assertEquals(0, block.saveCalls);
        assertTrue(state.errorsFor("name").stream().anyMatch(error -> error.contains("required")));
        assertTrue(state.errorsFor("count").stream().anyMatch(error -> error.contains("invalid")));
        assertEquals("not-a-number", state.fieldValues().get("count"));
        assertEquals("", state.fieldValues().get("id"));
        assertEquals("server", state.fieldValues().get("locked"));
        assertFalse(state.fieldValues().containsKey("unknown"));
    }

    @Test
    void field_changes_are_typed_and_mark_the_draft_dirty() {
        TestCreateBlock block = new TestCreateBlock();
        Harness harness = render(block, "/items/new", Map.of());

        formSegment(harness.root()).dispatch(new EditView.FieldChanged("count", "12"));
        harness.commands().runTasks();

        assertEquals(12, block.lastState.fieldValues().get("count"));
        assertTrue(block.lastState.isDirty());
        assertTrue(block.lastState.errorsFor("count").isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = (Map<String, Object>) block.blockMetadata().state().get("draft");
        assertEquals(12, draft.get("count"));
        assertEquals(true, block.blockMetadata().state().get("dirty"));
        assertFalse(draft.containsKey("secret"));
    }

    @Test
    void typed_persistence_failure_keeps_the_draft_and_exposes_feedback() {
        TestCreateBlock block = new TestCreateBlock();
        block.result = FormMutationResult.failure("Storage is unavailable.");
        Harness harness = render(block, "/items/new", Map.of());

        formSegment(harness.root()).dispatch(new EditView.FormValuesCollected(Map.of("name", "Draft", "count", "9")));
        harness.commands().runTasks();

        assertEquals(1, block.saveCalls);
        assertEquals(FormStatus.FAILED, block.lastState.status());
        assertEquals("Storage is unavailable.", block.lastState.message());
        assertEquals("Draft", block.lastState.fieldValues().get("name"));
        assertTrue(block.lastState.isDirty());
    }

    @Test
    void validation_exceptions_become_safe_form_feedback() {
        TestCreateBlock block = new TestCreateBlock() {
            @Override
            protected rsp.compositions.schema.ValidationResult validate(Map<String, Object> fieldValues) {
                throw new IllegalStateException("sensitive implementation detail");
            }
        };
        Harness harness = render(block, "/items/new", Map.of());

        formSegment(harness.root()).dispatch(
                new EditView.FormValuesCollected(Map.of("name", "Draft", "count", 4)));
        harness.commands().runTasks();

        assertEquals(FormStatus.FAILED, block.lastState.status());
        assertEquals("The item could not be saved.", block.lastState.message());
        assertFalse(block.lastState.message().contains("sensitive"));
        assertEquals(0, block.saveCalls);
    }

    @Test
    void successful_submission_stays_busy_until_scene_navigation_completes() {
        TestCreateBlock block = new TestCreateBlock();
        Harness harness = render(block, "/items/new", Map.of());

        ComponentSegment<EditView.EditViewState> segment = formSegment(harness.root());
        segment.dispatch(new EditView.FormValuesCollected(Map.of("name", "Saved", "count", 4)));
        harness.commands().runTasks();
        segment.dispatch(new EditView.FormValuesCollected(Map.of("name", "Saved twice", "count", 4)));
        harness.commands().runTasks();

        assertEquals(1, block.saveCalls);
        assertEquals(FormStatus.SUBMITTING, block.lastState.status());
        assertFalse(block.lastState.isDirty());
    }

    @Test
    void persistence_not_found_disables_further_mutations() {
        TestCreateBlock block = new TestCreateBlock();
        block.result = FormMutationResult.notFound("The item disappeared.");
        Harness harness = render(block, "/items/new", Map.of());

        formSegment(harness.root()).dispatch(
                new EditView.FormValuesCollected(Map.of("name", "Gone", "count", 4)));
        harness.commands().runTasks();

        assertEquals(FormStatus.NOT_FOUND, block.lastState.status());
        assertFalse(block.lastState.capabilities().canSave());
        assertFalse(block.lastState.capabilities().canDelete());
        assertEquals(List.of("cancel"), block.agentActions().stream().map(BlockAction::action).toList());
    }

    @Test
    void missing_edit_entity_has_a_non_mutating_not_found_state() {
        TestEditBlock block = new TestEditBlock(null);
        render(block, "/items/:id", Map.of("id", "404"));

        assertEquals(FormStatus.NOT_FOUND, block.initialState.status());
        assertFalse(block.initialState.capabilities().canSave());
        assertFalse(block.initialState.capabilities().canDelete());
        assertTrue(block.initialState.capabilities().canCancel());
        assertEquals(List.of("cancel"), block.agentActions().stream().map(BlockAction::action).toList());
    }

    @Test
    void typed_delete_failure_keeps_the_edit_form_available_with_feedback() {
        TestEditBlock block = new TestEditBlock(
                new TestItem("404", "Existing", 3, LocalDate.of(2026, 9, 1), "server"));
        block.deleteResult = FormMutationResult.failure("Delete was rejected.");
        Harness harness = render(block, "/items/:id", Map.of("id", "404"));
        assertEquals(FormStatus.READY, block.initialState.status());
        assertTrue(block.initialState.capabilities().canDelete());

        formSegment(harness.root()).dispatch(EditView.DeleteConfirmed.INSTANCE);
        harness.commands().runTasks();

        assertEquals(1, block.deleteCalls);
        assertEquals(FormStatus.FAILED, block.lastState.status());
        assertEquals("Delete was rejected.", block.lastState.message());
        assertTrue(block.lastState.capabilities().canDelete());
    }

    @Test
    void capabilities_remove_disallowed_agent_mutations() {
        TestCreateBlock block = new TestCreateBlock() {
            @Override protected boolean canSave() { return false; }
        };

        assertEquals(List.of("cancel"), block.agentActions().stream().map(BlockAction::action).toList());
    }

    @SuppressWarnings("unchecked")
    private static ComponentSegment<EditView.EditViewState> formSegment(ComponentSegment<String> root) {
        return (ComponentSegment<EditView.EditViewState>) root.directChildren().getFirst();
    }

    private static Harness render(FormBlock<?> block, String routePattern, Map<String, Object> showData) {
        RecordingCommands commands = new RecordingCommands();
        TreeBuilder treeBuilder = new TreeBuilder(new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"), new ComponentContext().with(CommandsEnqueue.class, commands), commands);
        Parent parent = new Parent(block, routePattern, showData);
        ComponentSegment<String> root = treeBuilder.openComponent(parent);
        root.render(treeBuilder);
        treeBuilder.closeComponent();
        assertTrue(treeBuilder.exceptions().isEmpty(), () -> treeBuilder.exceptions().toString());
        return new Harness(root, commands);
    }

    private record Harness(ComponentSegment<String> root, RecordingCommands commands) {
    }

    private static final class Parent extends Component<String, Object> {
        private final FormBlock<?> block;
        private final String routePattern;
        private final Map<String, Object> showData;

        private Parent(FormBlock<?> block, String routePattern, Map<String, Object> showData) {
            this.block = block;
            this.routePattern = routePattern;
            this.showData = showData;
        }

        @Override public ComponentStateSupplier<String> initStateSupplier() { return (_, _) -> "ready"; }
        @Override public ComponentView<String, Object> componentView() { return _ -> _ -> block; }

        @Override
        public java.util.function.BiFunction<ComponentContext, String, ComponentContext> subComponentsContext() {
            return (context, _) -> context
                    .with(ContextKeys.ROUTE_PATTERN, routePattern)
                    .with(ContextKeys.ROUTE_PATH, routePattern.replace(":id", "404"))
                    .with(ContextKeys.BLOCK_KEY, block.getClass())
                    .with(ContextKeys.SHOW_DATA, showData);
        }
    }

    private static class TestCreateBlock extends FormBlock<TestItem> {
        private static final DataSchema SCHEMA = DataSchema.builder()
                .field("id", FieldType.ID)
                .field("name", FieldType.STRING).required()
                .field("count", FieldType.INTEGER).defaultValue(5)
                .field("date", FieldType.DATE)
                .field("locked", FieldType.STRING).readOnly().defaultValue("server")
                .field("secret", FieldType.STRING).widget(rsp.compositions.schema.Widget.PASSWORD)
                .build();
        private EditView.EditViewState initialState;
        private EditView.EditViewState lastState;
        private int saveCalls;
        private FormMutationResult result = FormMutationResult.saved("1", "Saved.");

        private TestCreateBlock() { super(_ -> _ -> div()); }
        @Override public String title() { return "Create"; }
        @Override public DataSchema schema() { return SCHEMA; }
        @Override protected boolean isCreateMode() { return true; }
        @Override public boolean save(Map<String, Object> values) { return result.succeeded(); }
        @Override protected FormMutationResult saveResult(Map<String, Object> values) {
            saveCalls++;
            return result;
        }
        @Override protected void onBlockMounted(EditView.EditViewState state,
                                                StateUpdater<EditView.EditViewState> updater) {
            initialState = state;
            super.onBlockMounted(state, updater);
        }
        @Override public void onUpdated(rsp.component.ComponentCompositeKey id,
                                        EditView.EditViewState oldState,
                                        EditView.EditViewState newState,
                                        StateUpdater<EditView.EditViewState> updater) {
            lastState = newState;
        }
    }

    private static final class TestEditBlock extends EditBlock<TestItem> {
        private static final DataSchema SCHEMA = DataSchema.fromRecordClass(TestItem.class);
        private final TestItem item;
        private EditView.EditViewState initialState;
        private EditView.EditViewState lastState;
        private FormMutationResult deleteResult = FormMutationResult.saved("404", "Deleted.");
        private int deleteCalls;

        private TestEditBlock(TestItem item) { super(_ -> _ -> div()); this.item = item; }
        @Override public String title() { return "Edit"; }
        @Override public DataSchema schema() { return SCHEMA; }
        @Override protected String resolveIdFromPath(Lookup lookup) { return "404"; }
        @Override protected TestItem item(String id) { return item; }
        @Override public boolean save(Map<String, Object> values) { return true; }
        @Override protected boolean delete(String id) { return true; }
        @Override protected FormMutationResult deleteResult(String id) {
            deleteCalls++;
            return deleteResult;
        }
        @Override protected void onBlockMounted(EditView.EditViewState state,
                                                StateUpdater<EditView.EditViewState> updater) {
            initialState = state;
            super.onBlockMounted(state, updater);
        }
        @Override public void onUpdated(rsp.component.ComponentCompositeKey id,
                                        EditView.EditViewState oldState,
                                        EditView.EditViewState newState,
                                        StateUpdater<EditView.EditViewState> updater) {
            lastState = newState;
        }
    }

    public record TestItem(String id, String name, Integer count, LocalDate date, String locked) {
    }

    private static final class RecordingCommands implements CommandsEnqueue {
        private final List<Command> commands = new ArrayList<>();
        private int next;
        @Override public void offer(Command command) { commands.add(command); }
        private void runTasks() {
            while (next < commands.size()) {
                Command command = commands.get(next++);
                if (command instanceof GenericTaskEvent task) task.task().run();
            }
        }
    }
}
