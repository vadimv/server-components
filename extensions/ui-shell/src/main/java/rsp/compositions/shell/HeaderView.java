package rsp.compositions.shell;

import rsp.component.ComponentView;
import rsp.component.IntentDispatcher;
import rsp.dsl.Definition;

import static rsp.dsl.Html.*;

/**
 * HeaderView - Renders a horizontal stripe showing the active category name and auth status.
 * <p>
 * Receives authentication display data from {@link HeaderBlock}'s local state cache.
 * When authenticated, shows the principal and an optional sign-out link.
 */
public class HeaderView implements ComponentView<HeaderView.HeaderViewState, Object> {

    public record HeaderViewState(boolean authenticated, String username, String signOutHref) {
    }

    @Override
    public rsp.component.View<HeaderViewState> resolve(IntentDispatcher<Object> ignored) {
        return state -> div(attr("class", "layout-header"),
                authSection(state)
        );
    }

    private Definition authSection(HeaderViewState state) {
        if (!state.authenticated()) {
            return span();
        }
        if (state.signOutHref() == null) {
            return span(attr("class", "header-auth"),
                    span(attr("class", "header-username"), text(state.username())));
        }
        return span(attr("class", "header-auth"),
                span(attr("class", "header-username"), text(state.username())),
                a(attr("href", state.signOutHref()), attr("class", "header-signout"),
                        text("Sign out"))
        );
    }
}
