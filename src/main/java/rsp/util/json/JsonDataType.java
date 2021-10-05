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

        public Boolean(boolean value) {
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
        public boolean equals(java.lang.Object o) {
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

        public Number(long value) {
            this.value = value;
        }

        public Number(int value) {
            this.value = Long.valueOf(value);
        }

        public Number(byte value) {
            this.value = Long.valueOf(value);
        }

        public Number(short value) {
            this.value = Long.valueOf(value);
        }

        public Number(float value) {
            this.value = Double.valueOf(value);
        }

        public Number(double value) {
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
        public boolean equals(java.lang.Object o) {
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
        public String(java.lang.String value) {
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
            return "\"" + toString() + "\"";
        }

        @Override
        public boolean equals(java.lang.Object o) {
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

        public Object(Map<java.lang.String, JsonDataType> values) {
            this.values = values;
        }

        public Object() {
            this(Map.of());
        }

        public Optional<JsonDataType> value(java.lang.String name) {
            return Optional.ofNullable(values.get(name));
        }

        public Set<java.lang.String> keys() {
            return values.keySet();
        }

        @Override
        public java.lang.String toString() {
            return "{"
                    + java.lang.String.join(",",
                                            values.entrySet().stream().map(e -> "\"" + e.getKey()
                                                                                     + "\": \""
                                                                                     + e.getValue().toStringValue())
                                                             .collect(Collectors.toList()))
                    + '}';
        }

        @Override
        public boolean equals(java.lang.Object o) {
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
        private final JsonDataType[] items;

        public Array(JsonDataType... items) {
            this.items = items;
        }

        public JsonDataType[] items() {
            return items;
        }

        @Override
        public java.lang.String toString() {
            return "["
                    + java.lang.String.join(",",
                                            Arrays.stream(items).map(JsonDataType::toStringValue).collect(Collectors.toList()))
                    + ']';
        }

        @Override
        public boolean equals(java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final JsonDataType.Array array = (JsonDataType.Array) o;
            return Arrays.equals(items, array.items);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(items);
        }
    }

    /**
     * The JSON null type.
     */
    final class Null implements JsonDataType {
        public static Null INSTANCE = new Null();

        private Null() {}

        @Override
        public java.lang.String toString() {
            return "null";
        }

    }

    /**
     * Gives a string representation to be used in a JSON data field, quotes if needed.
     * @return the result string
     */
    default java.lang.String toStringValue() {
        return this.toString();
    }
}
