package rsp.compositions.auth;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContextKeys;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.RemoteCommand;

import static rsp.dsl.Html.*;

/**
 * A minimal login page that renders a single "Sign in" button.
 * <p>
 * On click, creates a server-side session, sets a cookie via JavaScript,
 * and reloads the page to the original URL (from the redirect query param).
 */
public class SimpleLoginComponent extends Component<SimpleLoginComponent.State> {

    private final SimpleAuthProvider authProvider;
    private CommandsEnqueue commandsEnqueue;
    private ComponentContext componentContext;

    public SimpleLoginComponent(SimpleAuthProvider authProvider) {
        super();
        this.authProvider = authProvider;
    }

    @Override
    public ComponentSegment<State> createComponentSegment(QualifiedSessionId sessionId,
                                                          TreePositionPath componentPath,
                                                          TreeBuilderFactory treeBuilderFactory,
                                                          ComponentContext componentContext,
                                                          CommandsEnqueue commandsEnqueue) {
        this.commandsEnqueue = commandsEnqueue;
        this.componentContext = componentContext;
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }

    @Override
    public ComponentStateSupplier<State> initStateSupplier() {
        return (_, context) -> {
            String redirect = context.get(ContextKeys.URL_QUERY.with("redirect"));
            return new State(redirect != null ? redirect : "/");
        };
    }

    @Override
    public ComponentView<State> componentView() {
        return _ -> state -> div(attr("class", "login-page"),
                h1(text("Sign In")),
                button(
                        attr("type", "button"),
                        on("click", ctx -> {
                            String token = authProvider.createSession();
                            commandsEnqueue.offer(new RemoteCommand.EvalJs(0,
                                    "document.cookie = '" + SimpleAuthProvider.SESSION_COOKIE_NAME
                                    + "=" + token + "; path=/; SameSite=Strict'"));
                            commandsEnqueue.offer(new RemoteCommand.SetHref(state.redirectPath()));
                        }),
                        text("Sign in")
                )
        );
    }

    public record State(String redirectPath) {}
}
