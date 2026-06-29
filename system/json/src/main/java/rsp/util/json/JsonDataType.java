package rsp.util.json;

import java.math.BigDecimal;
import java.util.*;

/**
 * A representation of the JSON data types as an immutable, sealed value tree.
 *
 * <p>Numbers are backed by {@link BigDecimal} so that any value the JSON grammar permits is
 * preserved without loss of precision or range (see {@link Number}).
 */
public sealed interface JsonDataType {

    /**
     * A boolean JSON data type.
     */
    record Boolean(boolean value) implements JsonDataType {

        @Override
        public java.lang.String toString() {
            return JsonWriter.write(this);
        }
    }

    /**
     * A numeric JSON data type, backed by an exact {@link BigDecimal}.
     *
     * <p>Using {@code BigDecimal} keeps the value 100% faithful to the JSON number grammar (RFC 8259
     * &sect;6): arbitrary integer magnitude, arbitrary decimal precision and exponent are all
     * preserved, and — unlike a {@code double} — the type can never hold {@code NaN}/{@code Infinity},
     * so a parsed number is always re-serialisable as valid JSON. Equality is by numeric value
     * ({@code 1.0} equals {@code 1.00}), implemented with {@link BigDecimal#compareTo}.
     */
    record Number(BigDecimal value) implements JsonDataType {

        public Number {
            Objects.requireNonNull(value);
        }

        public static Number of(final long value) {
            return new Number(BigDecimal.valueOf(value));
        }

        public static Number of(final int value) {
            return new Number(BigDecimal.valueOf(value));
        }

        public static Number of(final byte value) {
            return new Number(BigDecimal.valueOf(value));
        }

        public static Number of(final short value) {
            return new Number(BigDecimal.valueOf(value));
        }

        public static Number of(final float value) {
            return new Number(BigDecimal.valueOf(value));
        }

        public static Number of(final double value) {
            return new Number(BigDecimal.valueOf(value));
        }

        /** Whether the value has a non-zero fractional part. */
        public boolean isFractional() {
            return value.stripTrailingZeros().scale() > 0;
        }

        /** Whether the value is an integer (no fractional part). */
        public boolean isIntegral() {
            return !isFractional();
        }

        /** The value truncated towards zero to a {@code long} (may overflow for large magnitudes). */
        public long asLong() {
            return value.longValue();
        }

        /** The value as the nearest {@code double} (may lose precision or saturate to infinity). */
        public double asDouble() {
            return value.doubleValue();
        }

        @Override
        public java.lang.String toString() {
            return JsonWriter.write(this);
        }

        @Override
        public boolean equals(final java.lang.Object o) {
            return o instanceof Number n && value.compareTo(n.value) == 0;
        }

        @Override
        public int hashCode() {
            return value.stripTrailingZeros().hashCode();
        }
    }

    /**
     * A string JSON data type.
     */
    record String(java.lang.String value) implements JsonDataType {
        public static final JsonDataType.String EMPTY = new JsonDataType.String("");

        public String {
            Objects.requireNonNull(value);
        }

        @Override
        public java.lang.String toString() {
            return JsonWriter.write(this);
        }
    }

    /**
     * A JSON object, a collection of name/value pairs. Insertion order is preserved so serialised
     * output is deterministic. Duplicate keys follow last-wins semantics.
     */
    final class Object implements JsonDataType {
        public static final Object EMPTY = new Object(Collections.emptyMap());

        private final Map<java.lang.String, JsonDataType> values;

        public Object(final Map<java.lang.String, JsonDataType> values) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values)));
        }

        public Object() {
            this(Map.of());
        }

        public JsonDataType value(final java.lang.String name) {
            return values.get(name);
        }

        public Object put(final java.lang.String name, final JsonDataType value) {
            final Map<java.lang.String, JsonDataType> newValues = new LinkedHashMap<>(values);
            newValues.put(name, value);
            return new JsonDataType.Object(newValues);
        }

        public Set<java.lang.String> keys() {
            return values.keySet();
        }

        public Map<java.lang.String, JsonDataType> asMap() {
            return values;
        }

        @Override
        public java.lang.String toString() {
            return JsonWriter.write(this);
        }

        @Override
        public boolean equals(final java.lang.Object o) {
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
    record Array(JsonDataType... elements) implements JsonDataType {

        public Array {
            Objects.requireNonNull(elements);
        }

        public JsonDataType get(final int index) {
            return elements[index];
        }

        public int size() {
            return elements.length;
        }

        public boolean isEmpty() {
            return elements.length == 0;
        }

        @Override
        public java.lang.String toString() {
            return JsonWriter.write(this);
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
            return JsonWriter.write(this);
        }
    }

    /**
     * Thrown when JSON text is malformed or violates a configured {@link JsonLimits} bound. It is a
     * {@link RuntimeException} so callers are not forced to handle it, but a parser only ever throws
     * this type — never an {@link Error} such as {@link StackOverflowError} — for adversarial input.
     */
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
}
