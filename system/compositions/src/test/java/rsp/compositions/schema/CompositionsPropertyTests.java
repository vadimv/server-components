package rsp.compositions.schema;

import org.junit.jupiter.api.Test;
import rsp.component.Lookup;
import rsp.compositions.contract.ViewContract;
import rsp.compositions.routing.Router;
import rsp.pbt.Gen;
import rsp.pbt.Property;
import rsp.server.Path;

import java.lang.reflect.RecordComponent;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for compositions module invariants, using the in-house
 * {@link Gen}/{@link Property} harness.
 */
class CompositionsPropertyTests {

    // Test contract for Router tests
    static class TestContract extends ViewContract {
        TestContract(final Lookup lookup) {
            super(lookup);
        }

        @Override
        public String title() {
            return "Test";
        }

        @Override
        public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext context) {
            return context; // Test fixture - no enrichment needed
        }
    }

    // =====================
    // Router Properties
    // =====================

    @Test
    void any_path_with_correct_segment_count_matches_param_route() {
        Property.forAll(Gen.alpha(1, 20)).check(segment -> {
            final Router router = new Router().route("/a/:b", TestContract.class);
            final Path path = Path.of("/a/" + segment);

            assertTrue(router.match(path).isPresent(),
                    "Path '" + path + "' should match pattern '/a/:b'");
        });
    }

    @Test
    void multi_segment_param_route_matches_any_values() {
        Property.forAll(Gen.alpha(1, 20), Gen.alpha(1, 20)).check((postId, commentId) -> {
            final Router router = new Router().route("/posts/:postId/comments/:commentId", TestContract.class);
            final Path path = Path.of("/posts/" + postId + "/comments/" + commentId);

            assertTrue(router.match(path).isPresent(),
                    "Multi-param path should match pattern");
        });
    }

    @Test
    void wrong_segment_count_never_matches() {
        Property.forAll(Gen.alpha(1, 10)).check(extra -> {
            final Router router = new Router().route("/posts/:id", TestContract.class);

            // Too few segments
            assertFalse(router.match(Path.of("/posts")).isPresent(),
                    "Too few segments should not match");

            // Too many segments
            final Path tooMany = Path.of("/posts/123/" + extra);
            assertFalse(router.match(tooMany).isPresent(),
                    "Too many segments should not match");
        });
    }

    @Test
    void exact_route_only_matches_exact_path() {
        Property.forAll(Gen.alpha(1, 20)).check(different -> {
            Property.assume(!different.equals("posts"));

            final Router router = new Router().route("/posts", TestContract.class);

            assertTrue(router.match(Path.of("/posts")).isPresent(), "Exact path should match");
            assertFalse(router.match(Path.of("/" + different)).isPresent(),
                    "Different path should not match exact route");
        });
    }

    // =====================
    // DataSchema Properties
    // =====================

    @Test
    void schema_from_record_has_same_field_count_as_record_components() {
        Property.forAll(testRecords()).check(record -> {
            final DataSchema schema = DataSchema.fromFirstItem(record);
            final int componentCount = record.getClass().getRecordComponents().length;

            assertEquals(componentCount, schema.columns().size(),
                    "Schema column count should match record component count");
        });
    }

    @Test
    void to_map_contains_all_record_component_names() {
        Property.forAll(testRecords()).check(record -> {
            final DataSchema schema = DataSchema.fromFirstItem(record);
            final Map<String, Object> map = schema.toMap(record);

            for (final RecordComponent comp : record.getClass().getRecordComponents()) {
                assertTrue(map.containsKey(comp.getName()),
                        "Map should contain key for component: " + comp.getName());
            }
        });
    }

    @Test
    void to_map_preserves_record_values() {
        Property.forAll(testRecords()).check(record -> {
            final DataSchema schema = DataSchema.fromFirstItem(record);
            final Map<String, Object> map = schema.toMap(record);

            for (final RecordComponent comp : record.getClass().getRecordComponents()) {
                try {
                    final Object expected = comp.getAccessor().invoke(record);
                    final Object actual = map.get(comp.getName());
                    assertEquals(expected, actual,
                            "Map value should match record component value for: " + comp.getName());
                } catch (final Exception e) {
                    fail("Failed to access record component: " + comp.getName());
                }
            }
        });
    }

    @Test
    void hide_column_reduces_count_by_one() {
        Property.forAll(testRecords()).check(record -> {
            final DataSchema schema = DataSchema.fromFirstItem(record);
            Property.assume(schema.columns().size() > 0);

            final String firstColumn = schema.columns().get(0).name();
            final DataSchema hidden = schema.hideColumn(firstColumn);

            assertEquals(schema.columns().size() - 1, hidden.columns().size(),
                    "Hiding a column should reduce count by 1");
        });
    }

    @Test
    void rename_column_preserves_count() {
        Property.forAll(testRecords(), Gen.alpha(1, 20)).check((record, newName) -> {
            final DataSchema schema = DataSchema.fromFirstItem(record);
            Property.assume(schema.columns().size() > 0);

            final String firstColumn = schema.columns().get(0).name();
            final DataSchema renamed = schema.renameColumn(firstColumn, newName);

            assertEquals(schema.columns().size(), renamed.columns().size(),
                    "Renaming should not change column count");
        });
    }

    @Test
    void rename_column_changes_display_name() {
        Property.forAll(testRecords(), Gen.alpha(1, 20)).check((record, newDisplayName) -> {
            final DataSchema schema = DataSchema.fromFirstItem(record);
            Property.assume(schema.columns().size() > 0);

            final String firstColumn = schema.columns().get(0).name();
            final DataSchema renamed = schema.renameColumn(firstColumn, newDisplayName);

            assertEquals(newDisplayName, renamed.columns().get(0).displayName(),
                    "Display name should be updated");
            assertEquals(firstColumn, renamed.columns().get(0).name(),
                    "Column name should remain unchanged");
        });
    }

    // =====================
    // Generators
    // =====================

    private Gen<Object> testRecords() {
        return Gen.oneOf(
                simpleRecords(),
                multiFieldRecords(),
                nestedTypeRecords()
        );
    }

    private Gen<Object> simpleRecords() {
        return Gen.combine(
                Gen.alpha(1, 20),
                Gen.alpha(1, 50),
                (id, name) -> new SimpleRecord(id, name)
        );
    }

    private Gen<Object> multiFieldRecords() {
        return Gen.combine(
                Gen.alpha(1, 10),
                Gen.alpha(1, 30),
                Gen.integers(0, 1000),
                (id, title, count) -> new MultiFieldRecord(id, title, count)
        );
    }

    private Gen<Object> nestedTypeRecords() {
        return Gen.combine(
                Gen.longs(1, Long.MAX_VALUE),
                Gen.alpha(1, 20),
                Gen.booleans(),
                (id, label, active) -> new NestedTypeRecord(id, label, active)
        );
    }

    // Test record types
    record SimpleRecord(String id, String name) {}
    record MultiFieldRecord(String id, String title, int count) {}
    record NestedTypeRecord(long id, String label, boolean active) {}
}
