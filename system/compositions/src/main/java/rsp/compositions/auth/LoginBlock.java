package rsp.compositions.auth;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.compositions.block.ContextKeys;
import rsp.compositions.block.Block;
import rsp.page.events.RemoteCommand;
import rsp.url.Query;

import java.util.List;
import java.util.Objects;

import static rsp.dsl.Html.*;

/** Presentation-only sign-in block which navigates to an HTTP authentication endpoint. */
public class LoginBlock extends Block<LoginBlock.State, LoginBlock.SignInRequested> {
    private final String signInPath;
    private final boolean showDemoDescription;
    private CommandsEnqueue commandsEnqueue;

    public enum SignInRequested {
        INSTANCE
    }

    public record State(String signInHref, boolean showDemoDescription) {
    }

    public LoginBlock(String signInPath) {
        this(signInPath, false);
    }

    public LoginBlock(String signInPath, boolean showDemoDescription) {
        this.signInPath = Objects.requireNonNull(signInPath);
        this.showDemoDescription = showDemoDescription;
    }

    @Override
    public ComponentStateSupplier<State> initStateSupplier() {
        return (_, context) -> {
            String redirect = context.get(ContextKeys.URL_QUERY.with("redirect"));
            String target = redirect == null ? "/" : redirect;
            Query query = new Query(List.of(new Query.Parameter("redirect", target)));
            return new State(signInPath + query, showDemoDescription);
        };
    }

    @Override
    public ComponentView<State, SignInRequested> componentView() {
        return intents -> state -> div(attr("class", "login-page"),
                h1(text("Sign In")),
                state.showDemoDescription()
                        ? p(text("Clicking \"Sign in\" signs you in as the demo \"admin\" user."))
                        : of(),
                button(attr("type", "button"),
                        on("click", ctx -> intents.dispatch(SignInRequested.INSTANCE)),
                        text("Sign in")));
    }

    @Override
    protected void onBlockMounted(State state, StateUpdater<State> stateUpdate) {
        commandsEnqueue = lookup().get(CommandsEnqueue.class);
    }

    @Override
    protected void onIntent(SignInRequested intent, State state, StateUpdater<State> stateUpdater) {
        if (commandsEnqueue == null) {
            return;
        }
        commandsEnqueue.offer(new RemoteCommand.SetHref(state.signInHref()));
    }

    @Override
    public void onUnmounted(rsp.component.ComponentCompositeKey componentId, State state) {
        super.onUnmounted(componentId, state);
        commandsEnqueue = null;
    }

    @Override
    public String title() {
        return "Sign In";
    }
}
