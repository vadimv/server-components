package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class Conversion<S, T> implements InputComponent<S, T> {

    public final FieldComponent<S> component;
    public final Function<S, T> conversionFrom;
    public final Function<T, S> conversionTo;

    public Conversion(FieldComponent<S> component, Function<S, T> conversionFrom, Function<T, S> conversionTo) {
        this.component = component;
        this.conversionFrom = conversionFrom;
        this.conversionTo = conversionTo;
    }

    @Override
    public DocumentPartDefinition render(UseState<T> us) {
        return component.render(useState(() -> conversionTo.apply(us.get()), v -> us.accept(conversionFrom.apply(v))));
    }

    @Override
    public String key() {
        return component.key();
    }


    @Override
    public Function<S, T> conversionFrom() {
        return conversionFrom;
    }

    @Override
    public Function<T, S> conversionTo() {
        return conversionTo;
    }

    @Override
    public Function<S, Optional<String>>[] validations() {
        throw new IllegalStateException();
    }
}
