package rsp.util.json;

import java.util.*;

/**
 * A representation of the JSON data types.
 */
public sealed interface JsonDataType {

        /**
         * A boolean JSON data type.
         */
        record Boolean(boolean value) implements JsonDataType {

        @Override
        public java.lang.String toString() {
                return java.lang.Boolean.toString(value);
            }
    }

    /**
     * A numeric JSON data type.
     * Internally the number represented as a Long or Double value.
     */
    record Number(double value) implements JsonDataType {

        public static Number of(final long value) {
            return new Number(value);
        }

        public static Number of(final int value) {
            return new Number(value);
        }

        public static Number of(final byte value) {
            return new Number(value);
        }

        public static Number of(final short value) {
            return new Number((long) value);
        }

        public static Number of(final float value) {
            return new Number(value);
        }

        public static Number of(final double value) {
            return new Number(value);
        }

        public boolean isFractional() {
            return value != Math.floor(value);
        }

        public boolean isInfinite() {
            return Double.isInfinite(value);
        }

        public long asLong() {
            return (long) value;
        }

        public double value() {
            return value;
        }

        @Override
        public java.lang.String toString() {
            return isFractional() ? Double.toString(value) : Long.toString(asLong());
        }
    }

        /**
         * A string JSON data type.
         */
        record String(java.lang.String value) implements JsonDataType {
            public static final JsonDataType.String EMPTY = new JsonDataType.String("");

            @Override
            public java.lang.String toString() {
                return "\"" + value + "\"";
            }
    }

    /**
     * A JSON object, a collection of name/value pairs.
     */
    final class Object implements JsonDataType {
        public static final Object EMPTY = new Object(Collections.emptyMap());

        private final Map<java.lang.String, JsonDataType> values;

        public Object(final Map<java.lang.String, JsonDataType> values) {
            this.values = Map.copyOf(Objects.requireNonNull(values));
        }

        public Object() {
            this(Map.of());
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
                                                                                     + e.getValue().toString())
                                                             .toList())
                    + '}';
        }

        @Override
        public boolean equals(java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Object object = (Object) o;
            return values.equals(object.values);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
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

        public JsonDataType get(int index) {
            return elements[index];
        }

        public int size() {
            return elements.length;
        }

        @Override
        public java.lang.String toString() {
            return "["
                    + java.lang.String.join(",",
                                            Arrays.stream(elements).map(JsonDataType::toString).toList())
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
