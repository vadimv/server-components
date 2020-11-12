package rsp.examples.crud.state;

import java.util.Objects;

public class Cell {
    public final String fieldName;
    public final Object data;

    public Cell(String fieldName, Object data) {
        this.fieldName = Objects.requireNonNull(fieldName);
        this.data = Objects.requireNonNull(data);
    }

    @Override
    public String toString() {
        return data.toString();
    }
}