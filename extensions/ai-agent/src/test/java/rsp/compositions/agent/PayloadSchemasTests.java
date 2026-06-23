package rsp.compositions.agent;

import rsp.compositions.contract.ContractActionPayload;
import rsp.compositions.contract.PayloadSchemas;


import rsp.compositions.contract.PayloadSchema;


import org.junit.jupiter.api.Test;
import rsp.compositions.schema.DataSchema;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PayloadSchemasTests {

    // --- toParser ---

    @Test
    void none_parser_unwraps_value() {
        var parser = PayloadSchemas.toParser(new PayloadSchema.None());
        assertEquals("hello", parser.apply(ContractActionPayload.of("hello")));
    }

    @Test
    void string_parser_returns_string() {
        var parser = PayloadSchemas.toParser(new PayloadSchema.StringValue("id"));
        assertEquals("42", parser.apply(ContractActionPayload.of("42")));
    }

    @Test
    void integer_parser_returns_integer() {
        var parser = PayloadSchemas.toParser(new PayloadSchema.IntegerValue("page"));
        assertEquals(3, parser.apply(ContractActionPayload.of(3)));
    }

    @Test
    void stringSet_parser_wraps_single_string() {
        var parser = PayloadSchemas.toParser(new PayloadSchema.StringSet("ids"));
        assertEquals(Set.of("1"), parser.apply(ContractActionPayload.of("1")));
    }

    // --- describe ---

    @Test
    void none_returns_null() {
        assertNull(PayloadSchemas.describe(new PayloadSchema.None()));
    }

    @Test
    void stringValue_description() {
        assertEquals("String: row ID",
            PayloadSchemas.describe(new PayloadSchema.StringValue("row ID")));
    }

    @Test
    void integerValue_description() {
        assertEquals("Integer: page number",
            PayloadSchemas.describe(new PayloadSchema.IntegerValue("page number")));
    }

    @Test
    void stringSet_description() {
        assertEquals("Set<String>: row IDs",
            PayloadSchemas.describe(new PayloadSchema.StringSet("row IDs")));
    }

    @Test
    void objectValue_description() {
        var schema = new PayloadSchema.ObjectValue(List.of(
            new PayloadSchema.Property("title", "string", true, "Title"),
            new PayloadSchema.Property("count", "integer", false, "Count")
        ));
        String desc = PayloadSchemas.describe(schema);
        assertTrue(desc.contains("title:string"));
        assertTrue(desc.contains("count:integer"));
    }

    // --- toJsonSchema ---

    @Test
    void none_produces_empty_object_schema() {
        String json = PayloadSchemas.toJsonSchema(new PayloadSchema.None());
        assertEquals("{\"type\":\"object\",\"properties\":{}}", json);
    }

    @Test
    void stringValue_produces_string_schema() {
        String json = PayloadSchemas.toJsonSchema(new PayloadSchema.StringValue("row ID"));
        assertTrue(json.contains("\"type\":\"string\""));
        assertTrue(json.contains("\"required\":[\"payload\"]"));
    }

    @Test
    void integerValue_produces_integer_schema() {
        String json = PayloadSchemas.toJsonSchema(new PayloadSchema.IntegerValue("page"));
        assertTrue(json.contains("\"type\":\"integer\""));
    }

    @Test
    void stringSet_produces_array_schema() {
        String json = PayloadSchemas.toJsonSchema(new PayloadSchema.StringSet("ids"));
        assertTrue(json.contains("\"type\":\"array\""));
        assertTrue(json.contains("\"items\":{\"type\":\"string\"}"));
    }

    @Test
    void objectValue_produces_object_schema_with_required() {
        var schema = new PayloadSchema.ObjectValue(List.of(
            new PayloadSchema.Property("title", "string", true, "Title"),
            new PayloadSchema.Property("content", "string", false, "Content")
        ));
        String json = PayloadSchemas.toJsonSchema(schema);
        assertTrue(json.contains("\"title\":{\"type\":\"string\""));
        assertTrue(json.contains("\"content\":{\"type\":\"string\""));
        assertTrue(json.contains("\"required\":[\"title\"]"));
        assertFalse(json.contains("\"content\"") && json.contains("\"required\":[\"title\",\"content\"]"));
    }

    // --- fromDataSchema ---

    @Test
    void fromDataSchema_maps_visible_fields() {
        DataSchema schema = DataSchema.fromRecordClass(TestRecord.class);
        PayloadSchema.ObjectValue result = PayloadSchemas.fromDataSchema(schema);

        assertFalse(result.properties().isEmpty());
        assertTrue(result.properties().stream().anyMatch(p -> "name".equals(p.name())));
        assertTrue(result.properties().stream().anyMatch(p -> "count".equals(p.name())));
    }

    @Test
    void fromDataSchema_maps_field_types() {
        DataSchema schema = DataSchema.fromRecordClass(TestRecord.class);
        PayloadSchema.ObjectValue result = PayloadSchemas.fromDataSchema(schema);

        var nameField = result.properties().stream()
            .filter(p -> "name".equals(p.name())).findFirst().orElseThrow();
        assertEquals("string", nameField.type());

        var countField = result.properties().stream()
            .filter(p -> "count".equals(p.name())).findFirst().orElseThrow();
        assertEquals("integer", countField.type());
    }

    record TestRecord(String name, int count) {}
}
