package rsp.compositions.schema;

/** A selectable field value and its human-readable label. */
public record FieldChoice(String value, String label) {
    public FieldChoice {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("choice value is required");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("choice label is required");
        }
    }
}
