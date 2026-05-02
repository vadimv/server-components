package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ViewContract;

import java.util.Objects;

import static java.lang.System.Logger.Level.INFO;

/**
 * Header contract that displays auth status.
 * <p>
 * Reads auth data from context to display username and sign-out button.
 */
public class HeaderContract extends ViewContract {
    private static final System.Logger LOGGER = System.getLogger(HeaderContract.class.getName());

    public static final ContextKey.StringKey<Boolean> HEADER_AUTHENTICATED =
            new ContextKey.StringKey<>("header.authenticated", Boolean.class);
    public static final ContextKey.StringKey<String> HEADER_USERNAME =
            new ContextKey.StringKey<>("header.username", String.class);

    private final boolean authenticated;
    private final String username;
    private final AuthComponent.AuthProvider authProvider;
    private volatile String currentCategory;

    public HeaderContract(Lookup lookup) {
        super(lookup);
        Boolean auth = lookup.get(ContextKeys.AUTH_AUTHENTICATED);
        this.authenticated = Boolean.TRUE.equals(auth);
        Object user = lookup.get(ContextKeys.AUTH_USER);
        this.username = user != null ? user.toString() : "";
        this.authProvider = lookup.get(ContextKeys.AUTH_PROVIDER);

        /**
         * The code fragment below illustrates a contract-level context read and events watching for a sibling/companion contract.
         * @see ExplorerContract
         * @see PromptView
         */
        this.currentCategory = normalizeCategory(lookup.get(ContextKeys.PRIMARY_CATEGORY_KEY));
        logCurrentCategory("init", "", currentCategory);
        watch(ContextKeys.PRIMARY_CATEGORY_KEY, (previous, next) ->
                updateCurrentCategory("watch", previous, next));
    }

    @Override
    public String title() {
        return "Header";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        updateCurrentCategory("enrichContext", currentCategory, context.get(ContextKeys.PRIMARY_CATEGORY_KEY));
        ComponentContext enriched = context
                .with(HEADER_AUTHENTICATED, authenticated)
                .with(HEADER_USERNAME, username);
        if (authProvider != null) {
            enriched = enriched.with(ContextKeys.AUTH_PROVIDER, authProvider);
        }
        return enriched;
    }

    private void updateCurrentCategory(String source, String previous, String next) {
        String normalizedPrevious = normalizeCategory(previous);
        String normalizedNext = normalizeCategory(next);
        if (!Objects.equals(currentCategory, normalizedNext)) {
            currentCategory = normalizedNext;
            logCurrentCategory(source, normalizedPrevious, normalizedNext);
        }
    }

    private static String normalizeCategory(String category) {
        return category != null ? category : "";
    }

    private static void logCurrentCategory(String source, String previous, String current) {
        LOGGER.log(INFO, () -> "HeaderContract current category [" + source + "]: "
                + printable(previous) + " -> " + printable(current));
    }

    private static String printable(String category) {
        return category == null || category.isBlank() ? "<empty>" : category;
    }
}
