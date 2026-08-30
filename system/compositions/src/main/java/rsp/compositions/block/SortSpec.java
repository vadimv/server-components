package rsp.compositions.block;

import java.util.Objects;

/**
 * A single-column sort requested by a list view.
 *
 * @param field schema field name
 * @param direction sort direction
 */
public record SortSpec(String field, SortDirection direction) {
    public SortSpec {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("sort field is required");
        }
        field = field.trim();
        direction = Objects.requireNonNull(direction, "direction");
    }
}
