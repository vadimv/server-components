package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.State;
import rsp.examples.crud.entities.EntityService;
import rsp.state.UseState;

import static rsp.dsl.Html.*;

public class Resource<K, T> implements Component<State> {

    public final String name;
    public final EntityService<K, T> entityService;
    private final Component<Grid.GridState> listComponent;

    public Resource(String name, EntityService<K, T> entityService, Component<Grid.GridState> listComponent) {
        this.name = name;
        this.entityService = entityService;
        this.listComponent = listComponent;
    }

    @Override
    public DocumentPartDefinition of(UseState<State> us) {

        return div(
                when(us.get().viewName.equals("list"), listComponent.of(useState(() -> us.get().list)))
        );
    }

}
