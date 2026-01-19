package rsp.compositions;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import rsp.component.Lookup;
import rsp.component.TestLookup;

import java.lang.reflect.RecordComponent;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for compositions module invariants.
 */
class CompositionsPropertyTests {

    // Test contract for Router tests
    static class TestContract extends ViewContract {
        TestContract(final Lookup lookup) {
            super(lookup);
        }

        @Override
        public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext context) {
            return context; // Test fixture - no enrichment needed
        }
    }

    // =====================
    // Router Properties
    // =====================

    @Property
    void any_path_with_correct_segment_count_matches_param_route(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) final String segment) {
        final Router router = new Router().route("/a/:b", TestContract.class);
        final String path = "/a/" + segment;

        assertTrue(router.match(path).isPresent(),
                "Path '" + path + "' should match pattern '/a/:b'");
    }

    @Property
    void query_params_do_not_affect_matching(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) final String key,
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) final String value) {
        final Router router = new Router().route("/posts", TestContract.class);
        final String path = "/posts?" + key + "=" + value;

        assertTrue(router.match(path).isPresent(),
                "Path with query params should match base route");
    }

    @Property
    void multi_segment_param_route_matches_any_values(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) final String postId,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) final String commentId) {
        final Router router = new Router().route("/posts/:postId/comments/:commentId", TestContract.class);
        final String path = "/posts/" + postId + "/comments/" + commentId;

        assertTrue(router.match(path).isPresent(),
                "Multi-param path should match pattern");
    }

    @Property
    void wrong_segment_count_never_matches(
            @ForAll @AlphaChars @StringLength(min = 1, max = 10) final String extra) {
        final Router router = new Router().route("/posts/:id", TestContract.class);

        // Too few segments
        assertFalse(router.match("/posts").isPresent(),
                "Too few segments should not match");

        // Too many segments
        final String tooMany = "/posts/123/" + extra;
        assertFalse(router.match(tooMany).isPresent(),
                "Too many segments should not match");
    }

    @Property
    void exact_route_only_matches_exact_path(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) final String different) {
        Assume.that(!different.equals("posts"));

        final Router router = new Router().route("/posts", TestContract.class);

        assertTrue(router.match("/posts").isPresent(), "Exact path should match");
        assertFalse(router.match("/" + different).isPresent(),
                "Different path should not match exact route");
    }

    // =====================
    // DataSchema Properties
    // =====================

    @Property
    void schema_from_record_has_same_field_count_as_record_components(
            @ForAll("testRecords") final Object record) {
        final DataSchema schema = DataSchema.fromFirstItem(record);
        final int componentCount = record.getClass().getRecordComponents().length;

        assertEquals(componentCount, schema.columns().size(),
                "Schema column count should match record component count");
    }

    @Property
    void to_map_contains_all_record_component_names(
            @ForAll("testRecords") final Object record) {
        final DataSchema schema = DataSchema.fromFirstItem(record);
        final Map<String, Object> map = schema.toMap(record);

        for (final RecordComponent comp : record.getClass().getRecordComponents()) {
            assertTrue(map.containsKey(comp.getName()),
                    "Map should contain key for component: " + comp.getName());
        }
    }

    @Property
    void to_map_preserves_record_values(
            @ForAll("testRecords") final Object record) {
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
    }

    @Property
    void hide_column_reduces_count_by_one(
            @ForAll("testRecords") final Object record) {
        final DataSchema schema = DataSchema.fromFirstItem(record);
        Assume.that(schema.columns().size() > 0);

        final String firstColumn = schema.columns().get(0).name();
        final DataSchema hidden = schema.hideColumn(firstColumn);

        assertEquals(schema.columns().size() - 1, hidden.columns().size(),
                "Hiding a column should reduce count by 1");
    }

    @Property
    void rename_column_preserves_count(
            @ForAll("testRecords") final Object record,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) final String newName) {
        final DataSchema schema = DataSchema.fromFirstItem(record);
        Assume.that(schema.columns().size() > 0);

        final String firstColumn = schema.columns().get(0).name();
        final DataSchema renamed = schema.renameColumn(firstColumn, newName);

        assertEquals(schema.columns().size(), renamed.columns().size(),
                "Renaming should not change column count");
    }

    @Property
    void rename_column_changes_display_name(
            @ForAll("testRecords") final Object record,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) final String newDisplayName) {
        final DataSchema schema = DataSchema.fromFirstItem(record);
        Assume.that(schema.columns().size() > 0);

        final String firstColumn = schema.columns().get(0).name();
        final DataSchema renamed = schema.renameColumn(firstColumn, newDisplayName);

        assertEquals(newDisplayName, renamed.columns().get(0).displayName(),
                "Display name should be updated");
        assertEquals(firstColumn, renamed.columns().get(0).name(),
                "Column name should remain unchanged");
    }

    // =====================
    // Arbitrary Providers
    // =====================

    @Provide
    Arbitrary<Object> testRecords() {
        return Arbitraries.oneOf(
                simpleRecords(),
                multiFieldRecords(),
                nestedTypeRecords()
        );
    }

    private Arbitrary<Object> simpleRecords() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
        ).as(SimpleRecord::new);
    }

    private Arbitrary<Object> multiFieldRecords() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30),
                Arbitraries.integers().between(0, 1000)
        ).as(MultiFieldRecord::new);
    }

    private Arbitrary<Object> nestedTypeRecords() {
        return Combinators.combine(
                Arbitraries.longs().between(1, Long.MAX_VALUE),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.of(true, false)
        ).as(NestedTypeRecord::new);
    }

    // Test record types
    record SimpleRecord(String id, String name) {}
    record MultiFieldRecord(String id, String title, int count) {}
    record NestedTypeRecord(long id, String label, boolean active) {}
}
