package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Objects;

import static rsp.dsl.Html.text;

public class TextField<T> implements Component<String> {
    private final T data;

    public TextField(T data) {
        this.data = Objects.requireNonNull(data);
    }

    @Override
    public DocumentPartDefinition render(UseState<String> useState) {
        return text(data);
    }


}
