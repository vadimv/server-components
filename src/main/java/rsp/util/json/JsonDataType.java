package rsp.util.json;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A representation of the JSON data types.
 */
public interface JsonDataType {

    /**
     * A boolean JSON data type.
     */
    final class Boolean implements JsonDataType {
        private final boolean value;

        public Boolean(final boolean value) {
            this.value = value;
        }

        public boolean value() {
            return value;
        }

        @Override
        public java.lang.String toString() {
            return java.lang.Boolean.toString(value);
        }

        @Override
        public boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final JsonDataType.Boolean aBoolean = (JsonDataType.Boolean) o;
            return value == aBoolean.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    /**
     * A numeric JSON data type.
     * Internally the number represented as a Long or Double value.
     */
    final class Number implements JsonDataType {
        private final java.lang.Number value;

        public Number(final long value) {
            this.value = value;
        }

        public Number(final int value) {
            this.value = Long.valueOf(value);
        }

        public Number(final byte value) {
            this.value = Long.valueOf(value);
        }

        public Number(final short value) {
            this.value = Long.valueOf(value);
        }

        public Number(final float value) {
            this.value = Double.valueOf(value);
        }

        public Number(final double value) {
            this.value = value;
        }

        public boolean isFractional() {
            return value instanceof Double;
        }

        public long asLong() {
            return (long) value;
        }

        public double asDouble() {
            return (double) value;
        }

        public java.lang.Number value() {
            return value;
        }

        @Override
        public java.lang.String toString() {
            return value.toString();
        }

        @Override
        public boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final JsonDataType.Number number = (JsonDataType.Number) o;
            return Objects.equals(value, number.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    /**
     * A string JSON data type.
     */
    final class String implements JsonDataType {
        private final java.lang.String value;

        /**
         * Creates a new instance of a string JSON.
         * @param value unescaped
         */
        public String(final java.lang.String value) {
            this.value = value;
        }

        public java.lang.String value() {
            return value;
        }

        @Override
        public java.lang.String toString() {
            return value;
        }

        @Override
        public java.lang.String toStringValue() {
            return "\"" + this.toString() + "\"";
        }

        @Override
        public boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final JsonDataType.String string = (JsonDataType.String) o;
            return Objects.equals(value, string.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    /**
     * A JSON object, a collection of name/value pairs.
     */
    final class Object implements JsonDataType {
        public static final Object EMPTY = new Object(Collections.emptyMap());

        private final Map<java.lang.String, JsonDataType> values;

        Object(final Map<java.lang.String, JsonDataType> values) {
            this.values = values;
        }

        public Object() {
            this(Map.of());
        }

        public static Object of(final Map<java.lang.String, JsonDataType> values) {
            return new Object(Map.copyOf(values));
        }

        public Optional<JsonDataType> value(final java.lang.String name) {
            return Optional.ofNullable(values.get(name));
        }

        public Object put(final java.lang.String name, final JsonDataType value) {
            final Map<java.lang.String, JsonDataType> newValues = new HashMap<>(values);
            newValues.put(name, value);
            return new JsonDataType.Object(newValues);
        }

        public Set<java.lang.String> keys() {
            return values.keySet();
        }

        @Override
        public java.lang.String toString() {
            return "{"
                    + java.lang.String.join(",",
                                            values.entrySet().stream().map(e -> "\"" + e.getKey()
                                                                                     + "\": "
                                                                                     + e.getValue().toStringValue())
                                                             .toList())
                    + '}';
        }

        @Override
        public boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final JsonDataType.Object object = (JsonDataType.Object) o;
            return Objects.equals(values, object.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(values);
        }
    }

    /**
     * A JSON array, an ordered list of values.
     */
    final class Array implements JsonDataType {
        private final JsonDataType[] elements;

        public Array(final JsonDataType... elements) {
            this.elements = elements;
        }

        public JsonDataType[] elements() {
            return elements;
        }

        @Override
        public java.lang.String toString() {
            return "["
                    + java.lang.String.join(",",
                                            Arrays.stream(elements).map(JsonDataType::toStringValue).toList())
                    + ']';
        }

        @Override
        public boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final JsonDataType.Array array = (JsonDataType.Array) o;
            return Arrays.equals(elements, array.elements);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(elements);
        }
    }

    /**
     * The JSON null type.
     */
    final class Null implements JsonDataType {
        public static final Null INSTANCE = new Null();

        private Null() {}

        @Override
        public java.lang.String toString() {
            return "null";
        }

    }

    class JsonException extends RuntimeException {
        public JsonException() {
        }

        public JsonException(final java.lang.String message) {
            super(message);
        }

        public JsonException(final java.lang.String message, final Throwable cause) {
            super(message, cause);
        }

        public JsonException(final Throwable cause) {
            super(cause);
        }

        public JsonException(final java.lang.String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    /**
     * Creates a new instance of JsonDataType by parsing a string.
     * @param string the string to parse
     * @return the result JsonDataType object
     */
    static JsonDataType of(final java.lang.String string) {
        return JsonSimpleUtils.parse(string);
    }

    /**
     * Gives a string representation to be used in a JSON data field, quotes if needed.
     * @return the result string
     */
    default java.lang.String toStringValue() {
        return this.toString();
    }

    default Boolean asJsonBoolean() {
        if (this instanceof Boolean) {
            return (Boolean) this;
        } else {
            throw new JsonException("Unexpected JSON data type: " + this.getClass());
        }
    }

    default Number asJsonNumber() {
        if (this instanceof Number) {
            return (Number) this;
        } else {
            throw new JsonException("Unexpected JSON data type: " + this.getClass());
        }
    }

    default String asJsonString() {
        if (this instanceof String) {
            return (String) this;
        } else {
            throw new JsonException("Unexpected JSON data type: " + this.getClass());
        }
    }

    default Object asJsonObject() {
        if (this instanceof Object) {
            return (Object) this;
        } else {
            throw new JsonException("Unexpected JSON data type: " + this.getClass());
        }
    }

    default Array asJsonArray() {
        if (this instanceof Array) {
            return (Array) this;
        } else {
            throw new JsonException("Unexpected JSON data type: " + this.getClass());
        }
    }


}
