package rsp.compositions.shell;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.authentication.Authentication;
import rsp.compositions.block.Block;
import rsp.compositions.block.ContextKeys;

import java.util.Objects;


/**
 * Header block that displays auth status.
 * <p>
 * Reads identity data from context to display the principal and sign-out link.
 */
public class HeaderBlock extends Block<HeaderView.HeaderViewState, Object> {
    private static final System.Logger LOGGER = System.getLogger(HeaderBlock.class.getName());

    private final String signOutHref;
    private volatile String currentCategory;

    public HeaderBlock() {
        this(null);
    }

    public HeaderBlock(String signOutHref) {
        this.signOutHref = signOutHref;
    }

    @Override
    public String title() {
        return "Header";
    }

    @Override
    public ComponentStateSupplier<HeaderView.HeaderViewState> initStateSupplier() {
        return (_, context) -> {
            Authentication authentication = context.get(Authentication.class);
            boolean authenticated = authentication != null && authentication.isAuthenticated();
            Object principal = authenticated ? authentication.principal() : null;
            return new HeaderView.HeaderViewState(authenticated,
                    principal != null ? principal.toString() : "", signOutHref);
        };
    }

    @Override
    public ComponentView<HeaderView.HeaderViewState, Object> componentView() {
        return new HeaderView();
    }

    @Override
    protected void onBlockMounted(HeaderView.HeaderViewState state,
                                  StateUpdater<HeaderView.HeaderViewState> stateUpdate) {
        currentCategory = normalizeCategory(lookup().get(ContextKeys.PRIMARY_CATEGORY_KEY));
        logCurrentCategory("mount");
        watch(ContextKeys.PRIMARY_CATEGORY_KEY, (_, next) ->
                updateCurrentCategory("watch", next));
    }

    private void updateCurrentCategory(String source, String next) {
        String normalizedNext = normalizeCategory(next);
        if (!Objects.equals(currentCategory, normalizedNext)) {
            currentCategory = normalizedNext;
            logCurrentCategory(source);
        }
    }

    private static String normalizeCategory(String category) {
        return category != null ? category : "";
    }

    private static void logCurrentCategory(String source) {
        LOGGER.log(System.Logger.Level.DEBUG, () -> "Header block category changed [source=" + source + "]");
    }
}
