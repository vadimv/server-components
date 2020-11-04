package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.Html;

import java.util.List;

import static rsp.dsl.Html.*;

public class MenuPanel {

    public static final Component<MenuPanelState> component = state ->
            div(
                ul(
                    Html.of(state.get().names.stream().map(name -> li(a("#" + name, name)))
            )));

    public static class MenuPanelState {
        public final List<String> names;

        public MenuPanelState(List<String> names) {
            this.names = names;
        }
    }
}
