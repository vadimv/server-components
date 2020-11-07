package rsp.examples.crud.state;

import rsp.examples.crud.components.Grid;

import java.util.Objects;

public class Cell<T> {
    public final String fieldName;
    public final T data;

    public Cell(String fieldName, T data) {
        this.fieldName = Objects.requireNonNull(fieldName);
        this.data = Objects.requireNonNull(data);
    }

    @Override
    public String toString() {
        return data.toString();
    }
}