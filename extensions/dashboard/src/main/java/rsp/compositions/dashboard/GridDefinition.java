package rsp.compositions.dashboard;

import java.util.Objects;

/** Immutable dashboard grid settings. */
public record GridDefinition(int columns, int rowHeightPx, String gap) {
    public GridDefinition {
        if (columns < 1) {
            throw new IllegalArgumentException("Dashboard grid must have at least one column");
        }
        if (rowHeightPx < 1) {
            throw new IllegalArgumentException("Dashboard row height must be 1px or greater");
        }
        Objects.requireNonNull(gap, "gap");
        if (gap.isBlank()) {
            throw new IllegalArgumentException("Dashboard grid gap must not be blank");
        }
    }
}
