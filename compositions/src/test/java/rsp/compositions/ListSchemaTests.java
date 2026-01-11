package rsp.compositions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ListSchema record introspection and transformation.
 */
public class ListSchemaTests {

    // Test records
    record SimpleRecord(String id, String name) {}
    record TypedRecord(String id, int count, boolean active, double price) {}
    record SingleFieldRecord(String value) {}

    @Nested
    class FromRecordClassTests {

        @Test
        void extracts_all_record_components() {
            final ListSchema schema = ListSchema.fromRecordClass(SimpleRecord.class);

            assertEquals(2, schema.columns().size());
        }

        @Test
        void preserves_field_order() {
            final ListSchema schema = ListSchema.fromRecordClass(TypedRecord.class);

            final List<String> names = schema.columns().stream()
                    .map(ListSchema.ColumnDef::name)
                    .toList();

            assertEquals(List.of("id", "count", "active", "price"), names);
        }

        @Test
        void captures_correct_types() {
            final ListSchema schema = ListSchema.fromRecordClass(TypedRecord.class);

            final Map<String, Class<?>> typesByName = schema.columns().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            ListSchema.ColumnDef::name,
                            ListSchema.ColumnDef::type
                    ));

            assertEquals(String.class, typesByName.get("id"));
            assertEquals(int.class, typesByName.get("count"));
            assertEquals(boolean.class, typesByName.get("active"));
            assertEquals(double.class, typesByName.get("price"));
        }

        @Test
        void throws_on_non_record_class() {
            assertThrows(IllegalArgumentException.class, () ->
                ListSchema.fromRecordClass(String.class)
            );
        }

        @Test
        void handles_empty_record() {
            record EmptyRecord() {}

            final ListSchema schema = ListSchema.fromRecordClass(EmptyRecord.class);

            assertTrue(schema.columns().isEmpty());
        }
    }

    @Nested
    class FromFirstItemTests {

        @Test
        void extracts_schema_from_instance() {
            final SimpleRecord item = new SimpleRecord("1", "Test");

            final ListSchema schema = ListSchema.fromFirstItem(item);

            assertEquals(2, schema.columns().size());
            assertEquals("id", schema.columns().get(0).name());
            assertEquals("name", schema.columns().get(1).name());
        }

        @Test
        void handles_null_item() {
            final ListSchema schema = ListSchema.fromFirstItem(null);

            assertTrue(schema.columns().isEmpty());
        }
    }

    @Nested
    class ToMapConversionTests {

        @Test
        void converts_record_to_map() {
            final SimpleRecord item = new SimpleRecord("123", "Test Name");
            final ListSchema schema = ListSchema.fromRecordClass(SimpleRecord.class);

            final Map<String, Object> map = schema.toMap(item);

            assertEquals("123", map.get("id"));
            assertEquals("Test Name", map.get("name"));
        }

        @Test
        void handles_null_field_values() {
            final SimpleRecord item = new SimpleRecord("123", null);
            final ListSchema schema = ListSchema.fromRecordClass(SimpleRecord.class);

            final Map<String, Object> map = schema.toMap(item);

            assertEquals("123", map.get("id"));
            assertNull(map.get("name"));
        }

        @Test
        void handles_null_item() {
            final ListSchema schema = ListSchema.fromRecordClass(SimpleRecord.class);

            final Map<String, Object> map = schema.toMap(null);

            assertTrue(map.isEmpty());
        }

        @Test
        void throws_on_non_record_item() {
            final ListSchema schema = ListSchema.fromRecordClass(SimpleRecord.class);

            assertThrows(IllegalArgumentException.class, () ->
                schema.toMap("not a record")
            );
        }

        @Test
        void preserves_primitive_types() {
            final TypedRecord item = new TypedRecord("1", 42, true, 9.99);
            final ListSchema schema = ListSchema.fromRecordClass(TypedRecord.class);

            final Map<String, Object> map = schema.toMap(item);

            assertEquals(42, map.get("count"));
            assertEquals(true, map.get("active"));
            assertEquals(9.99, map.get("price"));
        }
    }

    @Nested
    class ToMapListConversionTests {

        @Test
        void converts_list_of_records() {
            final List<SimpleRecord> items = List.of(
                    new SimpleRecord("1", "First"),
                    new SimpleRecord("2", "Second")
            );
            final ListSchema schema = ListSchema.fromRecordClass(SimpleRecord.class);

            final List<Map<String, Object>> maps = schema.toMapList(items);

            assertEquals(2, maps.size());
            assertEquals("First", maps.get(0).get("name"));
            assertEquals("Second", maps.get(1).get("name"));
        }

        @Test
        void handles_empty_list() {
            final ListSchema schema = ListSchema.fromRecordClass(SimpleRecord.class);

            final List<Map<String, Object>> maps = schema.toMapList(List.of());

            assertTrue(maps.isEmpty());
        }
    }

    @Nested
    class SchemaCustomizationTests {

        @Test
        void rename_column_changes_display_name() {
            final ListSchema original = ListSchema.fromRecordClass(SimpleRecord.class);

            final ListSchema renamed = original.renameColumn("id", "Identifier");

            assertEquals("Identifier", renamed.columns().get(0).displayName());
            assertEquals("id", renamed.columns().get(0).name());  // Internal name unchanged
        }

        @Test
        void rename_column_preserves_other_columns() {
            final ListSchema original = ListSchema.fromRecordClass(SimpleRecord.class);

            final ListSchema renamed = original.renameColumn("id", "Identifier");

            assertEquals(2, renamed.columns().size());
            assertEquals("Name", renamed.columns().get(1).displayName());  // Unchanged
        }

        @Test
        void hide_column_removes_from_schema() {
            final ListSchema original = ListSchema.fromRecordClass(SimpleRecord.class);

            final ListSchema hidden = original.hideColumn("id");

            assertEquals(1, hidden.columns().size());
            assertEquals("name", hidden.columns().get(0).name());
        }

        @Test
        void hide_column_non_existent_no_change() {
            final ListSchema original = ListSchema.fromRecordClass(SimpleRecord.class);

            final ListSchema hidden = original.hideColumn("nonexistent");

            assertEquals(2, hidden.columns().size());
        }

        @Test
        void reorder_columns_changes_order() {
            final ListSchema original = ListSchema.fromRecordClass(SimpleRecord.class);

            final ListSchema reordered = original.reorderColumns("name", "id");

            assertEquals("name", reordered.columns().get(0).name());
            assertEquals("id", reordered.columns().get(1).name());
        }

        @Test
        void reorder_columns_with_subset_only_includes_specified() {
            final ListSchema original = ListSchema.fromRecordClass(TypedRecord.class);

            final ListSchema reordered = original.reorderColumns("price", "id");

            assertEquals(2, reordered.columns().size());
            assertEquals("price", reordered.columns().get(0).name());
            assertEquals("id", reordered.columns().get(1).name());
        }
    }

    @Nested
    class DisplayNameFormattingTests {

        @Test
        void simple_field_capitalized_first_letter() {
            final ListSchema.ColumnDef col = new ListSchema.ColumnDef("name", String.class);

            assertEquals("Name", col.displayName());
        }

        @Test
        void camel_case_converted_to_title_case() {
            final ListSchema.ColumnDef col = new ListSchema.ColumnDef("firstName", String.class);

            assertEquals("First Name", col.displayName());
        }

        @Test
        void multiple_camel_case_words() {
            final ListSchema.ColumnDef col = new ListSchema.ColumnDef("dateOfBirth", String.class);

            assertEquals("Date Of Birth", col.displayName());
        }

        @Test
        void single_character() {
            final ListSchema.ColumnDef col = new ListSchema.ColumnDef("x", String.class);

            assertEquals("X", col.displayName());
        }

        @Test
        void acronym_style_spaced_correctly() {
            final ListSchema.ColumnDef col = new ListSchema.ColumnDef("userID", String.class);

            // Each uppercase letter gets a space before it
            assertEquals("User I D", col.displayName());
        }
    }

    @Nested
    class ImmutabilityTests {

        @Test
        void customization_returns_new_instance() {
            final ListSchema original = ListSchema.fromRecordClass(SimpleRecord.class);

            final ListSchema renamed = original.renameColumn("id", "Identifier");

            assertNotSame(original, renamed);
            assertEquals("Id", original.columns().get(0).displayName());  // Original unchanged
        }

        @Test
        void hide_column_returns_new_instance() {
            final ListSchema original = ListSchema.fromRecordClass(SimpleRecord.class);

            final ListSchema hidden = original.hideColumn("id");

            assertNotSame(original, hidden);
            assertEquals(2, original.columns().size());  // Original unchanged
        }
    }
}
