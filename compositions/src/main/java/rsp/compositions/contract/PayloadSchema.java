package rsp.compositions.contract;

import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldDef;
import rsp.compositions.schema.FieldType;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes the payload type expected by an {@link AgentAction}.
 * <p>
 * Used to generate JSON Schema for LLM tool use APIs and to derive
 * payload parsers automatically.
 */
public sealed interface PayloadSchema {

    /**
     * No payload expected (for VoidKey events).
     */
    record None() implements PayloadSchema {}

    /**
     * Single string value.
     *
     * @param description what the string represents (e.g. "row ID")
     */
    record StringValue(String description) implements PayloadSchema {}

    /**
     * Single integer value.
     *
     * @param description what the integer represents (e.g. "page number (1-based)")
     */
    record IntegerValue(String description) implements PayloadSchema {}

    /**
     * Set of string values.
     *
     * @param description what the strings represent (e.g. "row IDs to delete")
     */
    record StringSet(String description) implements PayloadSchema {}

    /**
     * Structured object with named fields.
     *
     * @param properties the field definitions
     */
    record ObjectValue(List<Property> properties) implements PayloadSchema {
        public ObjectValue {
            properties = List.copyOf(properties);
        }
    }

    /**
     * A named field within an {@link ObjectValue} schema.
     *
     * @param name        field name
     * @param type        JSON Schema type ("string", "integer", "number", "boolean")
     * @param required    whether the field is required
     * @param description what the field represents
     */
    record Property(String name, String type, boolean required, String description) {}

    /**
     * Converts a {@link DataSchema} to an {@link ObjectValue}
     * by mapping visible, non-read-only fields.
     */
    static ObjectValue fromDataSchema(DataSchema dataSchema) {
        List<Property> properties = new ArrayList<>();
        for (FieldDef field : dataSchema.fields()) {
            if (field.isHidden() || field.isReadOnly()) {
                continue;
            }
            properties.add(new Property(
                field.name(),
                fieldTypeToJsonSchemaType(field.fieldType()),
                field.isRequired(),
                field.displayName()
            ));
        }
        return new ObjectValue(properties);
    }

    private static String fieldTypeToJsonSchemaType(FieldType fieldType) {
        return switch (fieldType) {
            case ID, STRING, TEXT, ENUM, DATE, DATETIME -> "string";
            case INTEGER -> "integer";
            case DECIMAL -> "number";
            case BOOLEAN -> "boolean";
        };
    }
}
