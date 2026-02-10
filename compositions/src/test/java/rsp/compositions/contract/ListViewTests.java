package rsp.compositions.contract;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.compositions.contract.ListView.ListViewState;
import rsp.compositions.schema.DataSchema;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ListViewTests {

    @Nested
    class ListViewStateSelectionTests {

        private static final DataSchema SCHEMA = DataSchema.fromRecordClass(TestRow.class);

        // Simple record for testing
        record TestRow(String id, String title) {}

        private static final List<Map<String, Object>> ROWS = List.of(
            Map.of("id", "1", "title", "First"),
            Map.of("id", "2", "title", "Second"),
            Map.of("id", "3", "title", "Third")
        );

        @Test
        void state_starts_with_empty_selection() {
            ListViewState state = new ListViewState(ROWS, SCHEMA, 1, "asc", "/items");

            assertTrue(state.selectedIds().isEmpty());
            assertFalse(state.isSelected("1"));
            assertFalse(state.isSelected("2"));
        }

        @Test
        void toggle_selection_adds_id() {
            ListViewState state = new ListViewState(ROWS, SCHEMA, 1, "asc", "/items");

            ListViewState newState = state.toggleSelection("1");

            assertTrue(newState.isSelected("1"));
            assertFalse(newState.isSelected("2"));
            assertEquals(1, newState.selectedIds().size());
        }

        @Test
        void toggle_selection_removes_id_when_already_selected() {
            ListViewState state = new ListViewState(ROWS, SCHEMA, 1, "asc", "/items",
                Set.of("1", "2"));

            ListViewState newState = state.toggleSelection("1");

            assertFalse(newState.isSelected("1"));
            assertTrue(newState.isSelected("2"));
            assertEquals(1, newState.selectedIds().size());
        }

        @Test
        void select_all_selects_all_rows() {
            ListViewState state = new ListViewState(ROWS, SCHEMA, 1, "asc", "/items");

            ListViewState newState = state.selectAll();

            assertTrue(newState.isSelected("1"));
            assertTrue(newState.isSelected("2"));
            assertTrue(newState.isSelected("3"));
            assertEquals(3, newState.selectedIds().size());
        }

        @Test
        void clear_selection_removes_all() {
            ListViewState state = new ListViewState(ROWS, SCHEMA, 1, "asc", "/items",
                Set.of("1", "2", "3"));

            ListViewState newState = state.clearSelection();

            assertTrue(newState.selectedIds().isEmpty());
            assertFalse(newState.isSelected("1"));
        }

        @Test
        void is_all_selected_true_when_all_rows_selected() {
            ListViewState state = new ListViewState(ROWS, SCHEMA, 1, "asc", "/items",
                Set.of("1", "2", "3"));

            assertTrue(state.isAllSelected());
        }

        @Test
        void is_all_selected_false_when_some_rows_not_selected() {
            ListViewState state = new ListViewState(ROWS, SCHEMA, 1, "asc", "/items",
                Set.of("1", "2"));

            assertFalse(state.isAllSelected());
        }

        @Test
        void is_all_selected_false_when_no_rows() {
            ListViewState state = new ListViewState(List.of(), SCHEMA, 1, "asc", "/items");

            assertFalse(state.isAllSelected());
        }

        @Test
        void selections_preserved_across_state_changes() {
            ListViewState state = new ListViewState(ROWS, SCHEMA, 1, "asc", "/items",
                Set.of("1", "2"));

            // Toggle a new selection
            ListViewState newState = state.toggleSelection("3");

            // Original selections still there
            assertTrue(newState.isSelected("1"));
            assertTrue(newState.isSelected("2"));
            assertTrue(newState.isSelected("3"));
        }

        @Test
        void state_is_immutable() {
            Set<String> initialIds = Set.of("1");
            ListViewState state = new ListViewState(ROWS, SCHEMA, 1, "asc", "/items",
                initialIds);

            ListViewState newState = state.toggleSelection("2");

            // Original state unchanged
            assertEquals(1, state.selectedIds().size());
            assertTrue(state.isSelected("1"));
            assertFalse(state.isSelected("2"));

            // New state has both
            assertEquals(2, newState.selectedIds().size());
        }
    }

    @Nested
    class ListViewStateDefaultsTests {

        @Test
        void backward_compatible_constructor_has_empty_selections() {
            ListViewState state = new ListViewState(
                List.of(Map.of("id", "1")),
                new DataSchema(List.of()),
                1,
                "asc",
                "/items"
            );

            assertTrue(state.selectedIds().isEmpty());
        }

        @Test
        void null_selectedIds_defaults_to_empty() {
            ListViewState state = new ListViewState(
                List.of(Map.of("id", "1")),
                new DataSchema(List.of()),
                1,
                "asc",
                "/items",
                null
            );

            assertNotNull(state.selectedIds());
            assertTrue(state.selectedIds().isEmpty());
        }
    }
}
