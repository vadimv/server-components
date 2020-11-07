package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import static rsp.dsl.Html.div;

public class Edit implements Component<Edit.State> {

    public Edit() {
    }

    @Override
    public DocumentPartDefinition render(UseState<State> useState) {
        return div("Edit component");
    }

    public static class State<K> {
        Grid.Row<K> editRow;
        public State() {
        }
    }
}
