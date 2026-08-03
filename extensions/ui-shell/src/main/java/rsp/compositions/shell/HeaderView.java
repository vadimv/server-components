package rsp.compositions.shell;

import rsp.component.ComponentView;
import rsp.component.IntentDispatcher;
import rsp.compositions.auth.AuthComponent;
import rsp.dsl.Definition;

import static rsp.dsl.Html.*;

/**
 * HeaderView - Renders a horizontal stripe showing the active category name and auth status.
 * <p>
 * Receives auth data from {@link HeaderContract}'s local state cache.
 * When authenticated, shows username and a "Sign out" button (if the auth provider supports it).
 */
public class HeaderView implements ComponentView<HeaderView.HeaderViewState, HeaderView.SignOutRequested> {

    public record HeaderViewState(boolean authenticated, String username, AuthComponent.AuthProvider authProvider) {
    }

    public enum SignOutRequested { INSTANCE }

    @Override
    public rsp.component.View<HeaderViewState> use(IntentDispatcher<SignOutRequested> intents) {
        return state -> div(attr("class", "layout-header"),
                authSection(state, intents)
        );
    }

    private Definition authSection(HeaderViewState state,
                                   IntentDispatcher<SignOutRequested> intents) {
        if (!state.authenticated()) {
            return span();
        }
        if (state.authProvider() == null || !state.authProvider().supportsSignOut()) {
            return span(attr("class", "header-auth"),
                    span(attr("class", "header-username"), text(state.username())));
        }
        return span(attr("class", "header-auth"),
                span(attr("class", "header-username"), text(state.username())),
                a(attr("href", "#"), attr("class", "header-signout"),
                        on("click", true, ctx -> intents.dispatch(SignOutRequested.INSTANCE)),
                        text("Sign out"))
        );
    }
}
