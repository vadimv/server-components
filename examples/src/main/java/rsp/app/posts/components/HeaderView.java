package rsp.app.posts.components;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.contract.ContextKeys;
import rsp.dom.TreePositionPath;
import rsp.dsl.Definition;
import rsp.page.QualifiedSessionId;

import static rsp.dsl.Html.*;

/**
 * HeaderView - Renders a horizontal stripe showing the active category name and auth status.
 * <p>
 * Reads the active category and auth data from context (set by {@link HeaderContract#enrichContext}).
 * When authenticated, shows username and a "Sign out" button (if the auth provider supports it).
 */
public class HeaderView extends Component<HeaderView.HeaderViewState> {

    private CommandsEnqueue commandsEnqueue;
    private AuthComponent.AuthProvider authProvider;

    public record HeaderViewState(String activeCategory, boolean authenticated, String username) {}

    @Override
    public ComponentSegment<HeaderViewState> createComponentSegment(QualifiedSessionId sessionId,
                                                                     TreePositionPath componentPath,
                                                                     TreeBuilderFactory treeBuilderFactory,
                                                                     ComponentContext componentContext,
                                                                     CommandsEnqueue commandsEnqueue) {
        this.commandsEnqueue = commandsEnqueue;
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }

    @Override
    public ComponentStateSupplier<HeaderViewState> initStateSupplier() {
        return (_, context) -> {
            String category = context.get(HeaderContract.HEADER_CATEGORY);
            Boolean auth = context.get(HeaderContract.HEADER_AUTHENTICATED);
            String username = context.get(HeaderContract.HEADER_USERNAME);
            this.authProvider = context.get(ContextKeys.AUTH_PROVIDER);
            return new HeaderViewState(
                    category != null ? category : "",
                    Boolean.TRUE.equals(auth),
                    username != null ? username : "");
        };
    }

    @Override
    public ComponentView<HeaderViewState> componentView() {
        return _ -> state -> div(attr("class", "layout-header"),
                span(attr("class", "header-category"), text(state.activeCategory())),
                authSection(state)
        );
    }

    private Definition authSection(HeaderViewState state) {
        if (!state.authenticated()) {
            return span();
        }
        if (authProvider == null || !authProvider.supportsSignOut()) {
            return span(attr("class", "header-auth"),
                    span(attr("class", "header-username"), text(state.username())));
        }
        return span(attr("class", "header-auth"),
                span(attr("class", "header-username"), text(state.username())),
                a(attr("href", "#"), attr("class", "header-signout"),
                        on("click", true, ctx -> authProvider.signOut(commandsEnqueue)),
                        text("Sign out"))
        );
    }
}
