package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Row;
import rsp.state.UseState;

import static rsp.dsl.Html.div;

public class Edit<K> implements Component<Edit.State<K>> {

    public Edit() {
    }


    @Override
    public DocumentPartDefinition render(UseState<State<K>> useState) {
        return div("Edit component");
    }

    public static class State<K> {
        Row<K> editRow;
        public State() {
        }
    }
}
