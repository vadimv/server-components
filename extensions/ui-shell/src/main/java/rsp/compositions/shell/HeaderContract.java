package rsp.compositions.shell;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.contract.ContractNodeComponent;
import rsp.compositions.contract.ContextKeys;

import java.util.Objects;


/**
 * Header contract that displays auth status.
 * <p>
 * Reads auth data from context to display username and sign-out button.
 */
public class HeaderContract extends ContractNodeComponent<HeaderView.HeaderViewState, HeaderView.SignOutRequested> {
    private static final System.Logger LOGGER = System.getLogger(HeaderContract.class.getName());

    private CommandsEnqueue commandsEnqueue;
    private volatile String currentCategory;

    @Override
    public String title() {
        return "Header";
    }

    @Override
    public ComponentStateSupplier<HeaderView.HeaderViewState> initStateSupplier() {
        return (_, context) -> {
            Boolean authenticated = context.get(ContextKeys.AUTH_AUTHENTICATED);
            Object user = context.get(ContextKeys.AUTH_USER);
            AuthComponent.AuthProvider authProvider = context.get(ContextKeys.AUTH_PROVIDER);
            return new HeaderView.HeaderViewState(Boolean.TRUE.equals(authenticated),
                    user != null ? user.toString() : "", authProvider);
        };
    }

    @Override
    public ComponentView<HeaderView.HeaderViewState, HeaderView.SignOutRequested> componentView() {
        return new HeaderView();
    }

    @Override
    protected void onContractMounted(HeaderView.HeaderViewState state,
                                     StateUpdater<HeaderView.HeaderViewState> stateUpdate) {
        commandsEnqueue = lookup().get(CommandsEnqueue.class);
        currentCategory = normalizeCategory(lookup().get(ContextKeys.PRIMARY_CATEGORY_KEY));
        logCurrentCategory("mount", "", currentCategory);
        watch(ContextKeys.PRIMARY_CATEGORY_KEY, (previous, next) ->
                updateCurrentCategory("watch", previous, next));
    }

    @Override
    protected void onIntent(HeaderView.SignOutRequested intent,
                            HeaderView.HeaderViewState state,
                            StateUpdater<HeaderView.HeaderViewState> stateUpdater) {
        AuthComponent.AuthProvider authProvider = state.authProvider();
        if (authProvider != null && authProvider.supportsSignOut() && commandsEnqueue != null) {
            authProvider.signOut(commandsEnqueue);
        }
    }

    @Override
    public void onUnmounted(rsp.component.ComponentCompositeKey componentId, HeaderView.HeaderViewState state) {
        super.onUnmounted(componentId, state);
        commandsEnqueue = null;
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
        LOGGER.log(System.Logger.Level.DEBUG, () -> "HeaderContract current category [" + source + "]: "
                + printable(previous) + " -> " + printable(current));
    }

    private static String printable(String category) {
        return category == null || category.isBlank() ? "<empty>" : category;
    }
}
