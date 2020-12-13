package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Optional;

import static rsp.dsl.Html.*;
import static rsp.state.UseState.useState;

public class LoginForm implements Component<LoginForm.State> {

    @Override
    public DocumentPartDefinition render(UseState<State> us) {
        return div(form(on("submit", c -> {
                }),
                new TextInput("login", "", s -> Optional.empty()).render(useState(() -> Optional.empty())),
                new TextInput("password", "", s -> Optional.empty()).render(useState(() -> Optional.empty())),
                button(attr("type", "submit"), text("Ok")),
                button(attr("type", "button"),
                        on("click", ctx -> {}),
                        text("Cancel"))));
    }

    public static class State {
        public final String login;
        public final String password;
        public final boolean loginFailed;

        public State(String login, String password, boolean loginFailed) {
            this.login = login;
            this.password = password;
            this.loginFailed = loginFailed;
        }
    }
}
