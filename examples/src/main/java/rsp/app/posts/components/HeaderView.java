package rsp.app.posts.components;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.auth.SimpleAuthProvider;
import rsp.dom.TreePositionPath;
import rsp.dsl.Definition;
import rsp.page.QualifiedSessionId;
import rsp.page.events.RemoteCommand;

import static rsp.dsl.Html.*;

/**
 * HeaderView - Renders a horizontal stripe showing the active category name and auth status.
 * <p>
 * Reads the active category and auth data from context (set by {@link HeaderContract#enrichContext}).
 * When authenticated, shows username and a "Sign out" button.
 */
public class HeaderView extends Component<HeaderView.HeaderViewState> {

    private CommandsEnqueue commandsEnqueue;

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
        return span(attr("class", "header-auth"),
                span(attr("class", "header-username"), text(state.username())),
                a(attr("href", "#"), attr("class", "header-signout"),
                        on("click", true, ctx -> {
                            commandsEnqueue.offer(new RemoteCommand.EvalJs(0,
                                    "document.cookie = '" + SimpleAuthProvider.SESSION_COOKIE_NAME
                                    + "=; path=/; max-age=0'"));
                            commandsEnqueue.offer(new RemoteCommand.SetHref("/"));
                        }),
                        text("Sign out"))
        );
    }
}
