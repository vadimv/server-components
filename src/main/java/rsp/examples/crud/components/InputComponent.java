package rsp.examples.crud.components;

import java.util.Optional;
import java.util.function.Function;

public interface InputComponent<S, T> extends FieldComponent<T> {
    Function<S, T> conversionFrom();
    Function<T, S> conversionTo();
    Function<S, Optional<String>>[] validations();

}
