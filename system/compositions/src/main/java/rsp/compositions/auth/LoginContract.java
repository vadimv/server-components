package rsp.compositions.auth;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ContractNodeComponent;
import rsp.page.events.RemoteCommand;

import java.util.Objects;

import static rsp.dsl.Html.*;

/** Intent-driven login contract for the demo and OAuth sign-in flows. */
public class LoginContract extends ContractNodeComponent<LoginContract.State, LoginContract.SignInRequested> {
    private final SimpleAuthProvider simpleAuthProvider;
    private final String oauthSignInPath;
    private CommandsEnqueue commandsEnqueue;

    public enum SignInRequested {
        INSTANCE
    }

    public record State(String redirectPath, boolean showDemoDescription) {
    }

    public LoginContract(SimpleAuthProvider simpleAuthProvider) {
        this.simpleAuthProvider = Objects.requireNonNull(simpleAuthProvider);
        this.oauthSignInPath = null;
    }

    public LoginContract(String oauthSignInPath) {
        this.simpleAuthProvider = null;
        this.oauthSignInPath = Objects.requireNonNull(oauthSignInPath);
    }

    @Override
    public ComponentStateSupplier<State> initStateSupplier() {
        return (_, context) -> {
            String redirect = context.get(ContextKeys.URL_QUERY.with("redirect"));
            return new State(redirect == null ? "/" : redirect, simpleAuthProvider != null);
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
    protected void onContractMounted(State state, StateUpdater<State> stateUpdate) {
        commandsEnqueue = lookup().get(CommandsEnqueue.class);
    }

    @Override
    protected void onIntent(SignInRequested intent, State state, StateUpdater<State> stateUpdater) {
        if (commandsEnqueue == null) {
            return;
        }
        if (simpleAuthProvider != null) {
            String token = simpleAuthProvider.createSession();
            commandsEnqueue.offer(new RemoteCommand.EvalJs(0,
                    "document.cookie = '" + SimpleAuthProvider.SESSION_COOKIE_NAME
                            + "=" + token + "; path=/; SameSite=Strict'"));
            commandsEnqueue.offer(new RemoteCommand.SetHref(state.redirectPath()));
            return;
        }
        commandsEnqueue.offer(new RemoteCommand.SetHref(oauthSignInPath + "?redirect=" + state.redirectPath()));
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
