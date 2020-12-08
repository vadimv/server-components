package rsp.examples.crud.components;

import rsp.Component;

import java.util.function.Function;

public class Column<T> {
    public final String title;
    public final Function<T, Component<String>> fieldComponent;

    public Column(String title, Function<T, Component<String>> fieldComponent) {
        this.title = title;
        this.fieldComponent = fieldComponent;
    }

    public Column(Function<T, Component<String>> fieldComponent) {
        this("", fieldComponent);
    }
}
