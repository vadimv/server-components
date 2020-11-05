package rsp.examples.crud;

import rsp.examples.crud.components.Grid;

public class State {

    public final String entityName;
    public final String viewName;
    public final Grid.GridState list;
    public State(String entityName,
                 String viewName,
                 Grid.GridState list) {
        this.entityName = entityName;
        this.viewName = viewName;
        this.list = list;
    }

    public State updateGridState(Grid.GridState gs) {
        return new State(entityName, viewName, gs);
    }
}
