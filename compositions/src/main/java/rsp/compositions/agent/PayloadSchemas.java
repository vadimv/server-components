package rsp.compositions.agent;

import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldDef;
import rsp.compositions.schema.FieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Utilities for working with {@link PayloadSchema}:
 * deriving parsers, generating JSON Schema strings, and converting from {@link DataSchema}.
 */
public final class PayloadSchemas {

    private PayloadSchemas() {}

    /**
     * Derives a payload parser from a schema variant.
     * The returned function converts an {@link AgentPayload} to the type expected by the event key.
     */
    public static Function<AgentPayload, Object> toParser(PayloadSchema schema) {
        return switch (schema) {
            case PayloadSchema.None _ -> p -> PayloadParsers.unwrap(p.value());
            case PayloadSchema.StringValue _ -> PayloadParsers.toStringPayload();
            case PayloadSchema.IntegerValue _ -> PayloadParsers.toInteger();
            case PayloadSchema.StringSet _ -> PayloadParsers.toSetOfStrings();
            case PayloadSchema.ObjectValue _ -> PayloadParsers.toMapOfStringObject();
        };
    }

    /**
     * Human-readable description of the payload, for system prompt fallback.
     * Returns null for {@link PayloadSchema.None}.
     */
    public static String describe(PayloadSchema schema) {
        return switch (schema) {
            case PayloadSchema.None _ -> null;
            case PayloadSchema.StringValue sv -> "String: " + sv.description();
            case PayloadSchema.IntegerValue iv -> "Integer: " + iv.description();
            case PayloadSchema.StringSet ss -> "Set<String>: " + ss.description();
            case PayloadSchema.ObjectValue ov -> {
                StringBuilder sb = new StringBuilder("Map<String, Object>: {");
                for (int i = 0; i < ov.properties().size(); i++) {
                    if (i > 0) sb.append(", ");
                    PayloadSchema.Property p = ov.properties().get(i);
                    sb.append(p.name()).append(":").append(p.type());
                }
                sb.append("}");
                yield sb.toString();
            }
        };
    }

    /**
     * Converts a schema to a JSON Schema string for LLM tool use APIs.
     * The top-level type is always "object" as required by tool use APIs.
     */
    public static String toJsonSchema(PayloadSchema schema) {
        return switch (schema) {
            case PayloadSchema.None _ ->
                "{\"type\":\"object\",\"properties\":{}}";
            case PayloadSchema.StringValue sv ->
                "{\"type\":\"object\",\"properties\":{\"payload\":{\"type\":\"string\",\"description\":\""
                    + escapeJson(sv.description()) + "\"}},\"required\":[\"payload\"]}";
            case PayloadSchema.IntegerValue iv ->
                "{\"type\":\"object\",\"properties\":{\"payload\":{\"type\":\"integer\",\"description\":\""
                    + escapeJson(iv.description()) + "\"}},\"required\":[\"payload\"]}";
            case PayloadSchema.StringSet ss ->
                "{\"type\":\"object\",\"properties\":{\"payload\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\""
                    + escapeJson(ss.description()) + "\"}},\"required\":[\"payload\"]}";
            case PayloadSchema.ObjectValue ov -> objectValueToJsonSchema(ov);
        };
    }

    /**
     * Converts a {@link DataSchema} to an {@link PayloadSchema.ObjectValue}
     * by mapping visible, non-read-only fields.
     */
    public static PayloadSchema.ObjectValue fromDataSchema(DataSchema dataSchema) {
        List<PayloadSchema.Property> properties = new ArrayList<>();
        for (FieldDef field : dataSchema.fields()) {
            if (field.isHidden() || field.isReadOnly()) {
                continue;
            }
            properties.add(new PayloadSchema.Property(
                field.name(),
                fieldTypeToJsonSchemaType(field.fieldType()),
                field.isRequired(),
                field.displayName()
            ));
        }
        return new PayloadSchema.ObjectValue(properties);
    }

    private static String objectValueToJsonSchema(PayloadSchema.ObjectValue ov) {
        StringBuilder sb = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        List<String> required = new ArrayList<>();
        boolean first = true;
        for (PayloadSchema.Property prop : ov.properties()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(prop.name())).append("\":{\"type\":\"")
                .append(escapeJson(prop.type())).append("\"");
            if (prop.description() != null && !prop.description().isEmpty()) {
                sb.append(",\"description\":\"").append(escapeJson(prop.description())).append("\"");
            }
            sb.append("}");
            if (prop.required()) {
                required.add(prop.name());
            }
        }
        sb.append("}");
        if (!required.isEmpty()) {
            sb.append(",\"required\":[");
            for (int i = 0; i < required.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(required.get(i))).append("\"");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String fieldTypeToJsonSchemaType(FieldType fieldType) {
        return switch (fieldType) {
            case ID, STRING, TEXT, ENUM, DATE, DATETIME -> "string";
            case INTEGER -> "integer";
            case DECIMAL -> "number";
            case BOOLEAN -> "boolean";
        };
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
