package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.state.UseState;

import java.util.List;

import static rsp.dsl.Html.*;

public class MenuPanel implements Component<MenuPanel.MenuPanelState> {

    @Override
    public DocumentPartDefinition render(UseState<MenuPanelState> state) {
        return div(
                    ul(
                        Html.of(state.get().names.stream().map(name -> li(a("/" + name, name)))
                    )));
    }

    public static class MenuPanelState {
        public final List<String> names;

        public MenuPanelState(List<String> names) {
            this.names = names;
        }
    }
}
