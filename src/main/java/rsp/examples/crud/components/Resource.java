package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.State;
import rsp.examples.crud.entities.EntityService;
import rsp.state.UseState;

import static rsp.dsl.Html.*;

public class Resource implements Component<State> {

    public final String name;
    private final Component<Grid.GridState> listComponent;

    public Resource(String name, Component<Grid.GridState> listComponent) {
        this.name = name;
        this.listComponent = listComponent;
    }

    @Override
    public DocumentPartDefinition of(UseState<State> us) {

        return div(
                when(us.get().viewName.equals("list"), listComponent.of(useState(() -> us.get().list)))
        );
    }
}
