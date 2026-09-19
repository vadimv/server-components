package rsp.http.openapi;

import rsp.util.json.Json;
import rsp.util.json.JsonDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Small immutable JSON Schema DSL for OpenAPI 3.1 route documentation. */
public final class OpenApiSchema {
    private final JsonDataType.Object value;

    private OpenApiSchema(JsonDataType.Object value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static OpenApiSchema any() {
        return new OpenApiSchema(Json.object());
    }

    public static OpenApiSchema string() {
        return typed("string");
    }

    public static OpenApiSchema integer() {
        return typed("integer");
    }

    public static OpenApiSchema number() {
        return typed("number");
    }

    public static OpenApiSchema bool() {
        return typed("boolean");
    }

    public static OpenApiSchema object() {
        return typed("object");
    }

    public static OpenApiSchema array(OpenApiSchema items) {
        return new OpenApiSchema(Json.object()
                .put("type", "array")
                .put("items", Objects.requireNonNull(items, "items").value));
    }

    public static OpenApiSchema ref(String componentName) {
        String name = requireComponentName(componentName);
        return new OpenApiSchema(Json.object().put("$ref", "#/components/schemas/" + name));
    }

    /** Uses an explicitly assembled JSON Schema object. */
    public static OpenApiSchema raw(JsonDataType.Object value) {
        return new OpenApiSchema(value);
    }

    public OpenApiSchema description(String description) {
        return put("description", OpenApiInfo.requireText(description, "description"));
    }

    public OpenApiSchema format(String format) {
        return put("format", OpenApiInfo.requireText(format, "format"));
    }

    public OpenApiSchema example(JsonDataType example) {
        return new OpenApiSchema(value.put("example", Objects.requireNonNull(example, "example")));
    }

    public OpenApiSchema enumeration(String... values) {
        Objects.requireNonNull(values, "values");
        if (values.length == 0) {
            throw new IllegalArgumentException("Schema enumeration must not be empty");
        }
        JsonDataType[] elements = Arrays.stream(values)
                .map(item -> Json.string(Objects.requireNonNull(item, "enum value")))
                .toArray(JsonDataType[]::new);
        return new OpenApiSchema(value.put("enum", Json.array(elements)));
    }

    public OpenApiSchema property(String name, OpenApiSchema schema) {
        requireObject();
        String propertyName = OpenApiInfo.requireText(name, "property name");
        JsonDataType.Object properties = value.value("properties") instanceof JsonDataType.Object existing
                ? existing : Json.object();
        return new OpenApiSchema(value.put("properties",
                properties.put(propertyName, Objects.requireNonNull(schema, "schema").value)));
    }

    public OpenApiSchema requiredProperty(String name, OpenApiSchema schema) {
        OpenApiSchema withProperty = property(name, schema);
        List<JsonDataType> required = new ArrayList<>();
        if (withProperty.value.value("required") instanceof JsonDataType.Array existing) {
            required.addAll(Arrays.asList(existing.elements()));
        }
        boolean present = required.stream()
                .anyMatch(item -> item instanceof JsonDataType.String text && text.value().equals(name));
        if (!present) {
            required.add(Json.string(name));
        }
        return new OpenApiSchema(withProperty.value.put("required",
                new JsonDataType.Array(required.toArray(JsonDataType[]::new))));
    }

    public OpenApiSchema additionalProperties(boolean allowed) {
        requireObject();
        return new OpenApiSchema(value.put("additionalProperties", Json.bool(allowed)));
    }

    public JsonDataType.Object json() {
        return value;
    }

    private OpenApiSchema put(String name, String text) {
        return new OpenApiSchema(value.put(name, text));
    }

    private void requireObject() {
        if (!(value.value("type") instanceof JsonDataType.String type) || !type.value().equals("object")) {
            throw new IllegalStateException("Properties can only be added to an object schema");
        }
    }

    private static OpenApiSchema typed(String type) {
        return new OpenApiSchema(Json.object().put("type", type));
    }

    private static String requireComponentName(String value) {
        String name = OpenApiInfo.requireText(value, "componentName");
        if (!name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid OpenAPI component name: " + name);
        }
        return name;
    }
}
