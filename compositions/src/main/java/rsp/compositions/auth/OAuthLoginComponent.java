package rsp.compositions.auth;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContextKeys;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.RemoteCommand;

import static rsp.dsl.Html.*;

/**
 * A login page that renders a "Sign in" button to start the OAuth PKCE flow.
 * <p>
 * On click, navigates to the sign-in path which triggers the PKCE redirect to the IdP.
 */
public class OAuthLoginComponent extends Component<OAuthLoginComponent.State> {

    private final String signinPath;
    private CommandsEnqueue commandsEnqueue;

    public OAuthLoginComponent(String signinPath) {
        super();
        this.signinPath = signinPath;
    }

    @Override
    public ComponentSegment<State> createComponentSegment(QualifiedSessionId sessionId,
                                                          TreePositionPath componentPath,
                                                          TreeBuilderFactory treeBuilderFactory,
                                                          ComponentContext componentContext,
                                                          CommandsEnqueue commandsEnqueue) {
        this.commandsEnqueue = commandsEnqueue;
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
                        on("click", ctx ->
                            commandsEnqueue.offer(new RemoteCommand.SetHref(
                                    signinPath + "?redirect=" + state.redirectPath()))),
                        text("Sign in")
                )
        );
    }

    public record State(String redirectPath) {}
}
