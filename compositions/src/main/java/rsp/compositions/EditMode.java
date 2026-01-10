package rsp.compositions;

/**
 * EditMode - Configurable modes for create/edit workflows.
 * <p>
 * Determines how the "create new" action is handled in terms of navigation and UI.
 */
public enum EditMode {
    /**
     * Navigate to a dedicated create page with a special path token.
     * <p>
     * URL changes: /posts → /posts/new
     * <p>
     * Pros: Clean URLs, RESTful, bookmarkable, simple implementation
     * Cons: Full page navigation, create token is reserved
     */
    SEPARATE_PAGE,

    /**
     * Show create form based on query parameter, list remains in URL path.
     * <p>
     * URL changes: /posts → /posts?create=true
     * <p>
     * Pros: Bookmarkable, back button closes modal, list stays visible
     * Cons: URL less clean, requires LayoutComponent enhancement
     */
    QUERY_PARAM,

    /**
     * Modal state managed entirely in components, no URL change.
     * <p>
     * URL stays: /posts (same when modal is open)
     * <p>
     * Pros: App-like UX, simplest URL structure, no special routes
     * Cons: Not bookmarkable, refresh loses modal state, back button doesn't close modal
     */
    MODAL
}
