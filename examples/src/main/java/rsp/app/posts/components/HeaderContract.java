package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ViewContract;

/**
 * Header contract that displays auth status.
 * <p>
 * Reads auth data from context to display username and sign-out button.
 */
public class HeaderContract extends ViewContract {

    public static final ContextKey.StringKey<Boolean> HEADER_AUTHENTICATED =
            new ContextKey.StringKey<>("header.authenticated", Boolean.class);
    public static final ContextKey.StringKey<String> HEADER_USERNAME =
            new ContextKey.StringKey<>("header.username", String.class);

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
    }

    @Override
    public String title() {
        return "Header";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        ComponentContext enriched = context
                .with(HEADER_AUTHENTICATED, authenticated)
                .with(HEADER_USERNAME, username);
        if (authProvider != null) {
            enriched = enriched.with(ContextKeys.AUTH_PROVIDER, authProvider);
        }
        return enriched;
    }
}
