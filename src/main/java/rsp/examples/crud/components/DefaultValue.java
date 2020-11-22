package rsp.examples.crud.components;

public class DefaultValue<T> {
    public final String fieldName;
    public final T value;

    public DefaultValue(String fieldName, T value) {
        this.fieldName = fieldName;
        this.value = value;
    }
}
