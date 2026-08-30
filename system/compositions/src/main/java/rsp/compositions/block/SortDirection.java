package rsp.compositions.block;

import java.util.Locale;

/** Direction used by a schema-backed list sort. */
public enum SortDirection {
    ASC,
    DESC;

    /** Parse a case-insensitive query value, returning the supplied fallback when invalid. */
    public static SortDirection parse(String value, SortDirection fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /** Lower-case value used in grid URLs. */
    public String queryValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Direction used by the next toggle of an active sort column. */
    public SortDirection opposite() {
        return this == ASC ? DESC : ASC;
    }
}
