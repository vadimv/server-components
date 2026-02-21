package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.contract.Capabilities;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ViewContract;

/**
 * Header contract that displays the active category name and auth status.
 * <p>
 * Subscribes to the {@link Capabilities#ACTIVE_CATEGORY} capability
 * published by the primary contract (e.g., "Posts", "Comments").
 * Reads auth data from context to display username and sign-out button.
 */
public class HeaderContract extends ViewContract {

    public static final ContextKey.StringKey<String> HEADER_CATEGORY =
            new ContextKey.StringKey<>("header.category", String.class);
    public static final ContextKey.StringKey<Boolean> HEADER_AUTHENTICATED =
            new ContextKey.StringKey<>("header.authenticated", Boolean.class);
    public static final ContextKey.StringKey<String> HEADER_USERNAME =
            new ContextKey.StringKey<>("header.username", String.class);

    private String activeCategory = "";
    private final boolean authenticated;
    private final String username;
    private final AuthComponent.AuthProvider authProvider;

    public HeaderContract(Lookup lookup) {
        super(lookup);
        Boolean auth = lookup.get(ContextKeys.AUTH_AUTHENTICATED);
        this.authenticated = Boolean.TRUE.equals(auth);
        Object user = lookup.get(ContextKeys.AUTH_USER);
        this.username = user != null ? user.toString() : "";
        this.authProvider = lookup.get(ContextKeys.AUTH_PROVIDER);
        onCapability(Capabilities.ACTIVE_CATEGORY, category -> {
            this.activeCategory = category;
        });
    }

    @Override
    public String title() {
        return "Header";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        ComponentContext enriched = context
                .with(HEADER_CATEGORY, activeCategory)
                .with(HEADER_AUTHENTICATED, authenticated)
                .with(HEADER_USERNAME, username);
        if (authProvider != null) {
            enriched = enriched.with(ContextKeys.AUTH_PROVIDER, authProvider);
        }
        return enriched;
    }
}
