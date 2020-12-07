package rsp.examples.crud.components;

import rsp.Component;

import java.util.function.BiFunction;

public class Column<T> {
    public final String title;
    public final BiFunction<String, T, Component> fieldComponent;

    public Column(String title, BiFunction<String, T, Component> fieldComponent) {
        this.title = title;
        this.fieldComponent = fieldComponent;
    }

    public Column(BiFunction<String, T, Component> fieldComponent) {
        this("", fieldComponent);
    }
}
