package rsp.compositions.schema;

/**
 * Semantic metadata for a scalar field that references another resource.
 *
 * <p>The resource key is intentionally independent of repositories and routes.
 * Blocks resolve it to authorized choices and navigation targets for the current
 * application.</p>
 */
public record ReferenceDef(String resourceKey) {
    public ReferenceDef {
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("resourceKey is required");
        }
    }
}
