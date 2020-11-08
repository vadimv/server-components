package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Row;
import rsp.state.UseState;

import java.util.Optional;

import static rsp.dsl.Html.div;

public class Edit<K> implements Component<Optional<Row<K>>> {

    public Edit() {
    }


    @Override
    public DocumentPartDefinition render(UseState<Optional<Row<K>>> useState) {
        return div("Edit component:" + useState.get().get().key);
    }
}
