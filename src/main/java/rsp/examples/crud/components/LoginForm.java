package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.Author;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.Optional;

import static rsp.dsl.Html.*;
import static rsp.state.UseState.useState;

public class LoginForm implements Component<LoginForm.State> {

    @Override
    public DocumentPartDefinition render(UseState<State> us) {
        return div(new Form(m -> m.apply("login").flatMap(login -> m.apply("password")
                                                   .map(password -> new Tuple2<>(login, password)))
                                                   .ifPresent(lp -> us.accept(new State(lp._1, lp._2, false))),
                             new TextInput("login", "Login", us.get().login),
                             new TextInput("password", "Password", us.get().password))
                          .render(useState(() -> new Form.State())),
                   when(us.get().loginFailed, span("Incorrect username or password.")));
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

        public State() {
            this("", "", false);
        }
    }
}
