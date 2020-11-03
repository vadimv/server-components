package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.examples.crud.State;
import rsp.state.UseState;

import java.util.Arrays;

import static rsp.dsl.Html.*;

public class Admin implements Component<State> {
    private final Resource[] resources;
    public Admin(Resource... resources) {
        this.resources = resources;
    }

    @Override
    public DocumentPartDefinition of(UseState<State> useState) {
        return html(
                body(
                        MenuPanel.component.of(useState(() -> new MenuPanel.MenuPanelState())),

                        Html.of(Arrays.stream(resources).filter(resource -> resource.name.equals(useState.get().entityName))
                                                        .map(resource -> resource.of(useState))

                )));
    }
}
