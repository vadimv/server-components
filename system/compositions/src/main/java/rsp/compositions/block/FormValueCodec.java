package rsp.compositions.block;

import rsp.compositions.schema.FieldDef;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Converts untrusted browser or agent field values to schema-declared Java types. */
public final class FormValueCodec {
    private FormValueCodec() {
    }

    /** Sentinel used when a browser property could not be read. */
    public enum Unavailable {
        INSTANCE
    }

    public record Conversion(Object value, String error) {
        public Conversion {
            error = error == null ? "" : error;
        }

        public boolean valid() {
            return error.isEmpty();
        }
    }

    public static Conversion convert(FieldDef field, Object rawValue) {
        if (rawValue == Unavailable.INSTANCE) {
            return failure(field, "could not be read");
        }
        Class<?> type = field.type();
        if (rawValue == null) {
            return new Conversion(null, "");
        }
        if (type.isInstance(rawValue)) {
            return new Conversion(rawValue, "");
        }

        String text = String.valueOf(rawValue);
        if (text.isBlank()) {
            if (type == String.class) return new Conversion("", "");
            if (type == boolean.class) return new Conversion(false, "");
            if (type.isPrimitive()) return failure(field, "cannot be blank");
            return new Conversion(null, "");
        }

        try {
            if (type == String.class) return new Conversion(text, "");
            if (type == Integer.class || type == int.class) return new Conversion(Integer.valueOf(text), "");
            if (type == Long.class || type == long.class) return new Conversion(Long.valueOf(text), "");
            if (type == Short.class || type == short.class) return new Conversion(Short.valueOf(text), "");
            if (type == Byte.class || type == byte.class) return new Conversion(Byte.valueOf(text), "");
            if (type == Double.class || type == double.class) return new Conversion(Double.valueOf(text), "");
            if (type == Float.class || type == float.class) return new Conversion(Float.valueOf(text), "");
            if (type == BigDecimal.class) return new Conversion(new BigDecimal(text), "");
            if (type == BigInteger.class) return new Conversion(new BigInteger(text), "");
            if (type == Boolean.class || type == boolean.class) return booleanValue(field, rawValue, text);
            if (type == LocalDate.class) return new Conversion(LocalDate.parse(text), "");
            if (type == LocalDateTime.class) return new Conversion(LocalDateTime.parse(text), "");
            if (type.isEnum()) return enumValue(field, type, text);
        } catch (RuntimeException ignored) {
            return failure(field, "has an invalid " + field.displayName().toLowerCase() + " value");
        }
        return failure(field, "has an unsupported value type");
    }

    public static Object initialValue(FieldDef field) {
        Object configured = field.options().defaultValue();
        if (configured != null) {
            Conversion converted = convert(field, configured);
            if (!converted.valid()) {
                throw new IllegalArgumentException("Invalid default for " + field.name() + ": " + converted.error());
            }
            return converted.value();
        }
        if (field.type() == String.class) return "";
        if (field.type() == Boolean.class || field.type() == boolean.class) return false;
        if (field.type().isPrimitive()) return primitiveDefault(field.type());
        return null;
    }

    public static String formatForInput(Object value) {
        if (value == null) return "";
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        return String.valueOf(value);
    }

    private static Conversion booleanValue(FieldDef field, Object rawValue, String text) {
        if (rawValue instanceof Boolean value) return new Conversion(value, "");
        return switch (text.toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "on", "yes" -> new Conversion(true, "");
            case "false", "0", "off", "no" -> new Conversion(false, "");
            default -> failure(field, "must be true or false");
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Conversion enumValue(FieldDef field, Class<?> type, String text) {
        for (Object constant : type.getEnumConstants()) {
            Enum enumValue = (Enum) constant;
            if (enumValue.name().equalsIgnoreCase(text) || enumValue.toString().equals(text)) {
                return new Conversion(enumValue, "");
            }
        }
        return failure(field, "must be one of the configured options");
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        return null;
    }

    private static Conversion failure(FieldDef field, String detail) {
        return new Conversion(null, field.displayName() + " " + detail);
    }
}
