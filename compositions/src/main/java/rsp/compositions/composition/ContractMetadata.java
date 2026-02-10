package rsp.compositions.composition;

import java.util.Objects;

/**
 * ContractMetadata - Derived navigation metadata for a contract.
 *
 * @param categoryKey key used for grouping/highlighting in navigation (e.g., "Posts")
 * @param navigationLabel display label for navigation entry
 */
public record ContractMetadata(String categoryKey, String navigationLabel) {
    public ContractMetadata {
        Objects.requireNonNull(categoryKey, "categoryKey");
        Objects.requireNonNull(navigationLabel, "navigationLabel");
    }
}
