package rsp.examples.crud.components;

import rsp.Component;

import static rsp.dsl.Html.*;

public class MenuPanel {

    public static final Component<MenuPanelState> component = state ->
            div(
                ul(
                    li(a("#first", "first")),
                    li(a("#second", "second"))
            ));

    public static class MenuPanelState {

    }
}
