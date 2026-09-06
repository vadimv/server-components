package rsp.compositions.block;

import java.util.Objects;

/**
 * Declares an inverse relationship link rendered as an appended list column.
 * The owning {@link ListBlock} resolves the target block key to a concrete route.
 */
public record RelatedListLinkSpec(String key,
                                  String label,
                                  String sourceField,
                                  Object targetBlockKey,
                                  String filterField,
                                  String linkLabel) {
    public RelatedListLinkSpec {
        requireText(key, "key");
        requireText(label, "label");
        requireText(sourceField, "sourceField");
        Objects.requireNonNull(targetBlockKey, "targetBlockKey");
        requireText(filterField, "filterField");
        requireText(linkLabel, "linkLabel");
    }

    public static RelatedListLinkSpec to(String key,
                                         String label,
                                         String sourceField,
                                         Object targetBlockKey,
                                         String filterField,
                                         String linkLabel) {
        return new RelatedListLinkSpec(key, label, sourceField, targetBlockKey, filterField, linkLabel);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
