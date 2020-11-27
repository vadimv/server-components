package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.state.UseState;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Edit<T> implements Component<Edit.State<T>> {

    private final Function<UseState<T>, Form<T>> formFunction;
    public Edit(Function<UseState<T>, Form<T>> formFunction) {
        this.formFunction = formFunction;
    }


    @Override
    public DocumentPartDefinition render(UseState<Edit.State<T>> us) {
        return div(span("Edit"),
                   formFunction.apply(useState(() -> us.get().current.get().data,
                                            v -> us.accept(us.get().withValue(v).withValidationErrors(Collections.EMPTY_MAP))))
                                                   .render(useState(() -> new Form.State<>(us.get().current.map(v -> v.data), us.get().validationErrors),
                                                                     v -> us.accept(us.get().withValidationErrors(v.validationErrors)))));
    }


    public static class State<T> {
        public final boolean isActive;
        public final Optional<KeyedEntity<String, T>> current;
        public final Map<String, String> validationErrors;
        public State(boolean isActive, Optional<KeyedEntity<String, T>> data, Map<String, String> validationErrors) {
            this.isActive = isActive;
            this.current = data;
            this.validationErrors = validationErrors;
        }
        public State(boolean isActive, Optional<KeyedEntity<String, T>> data) {
            this(isActive, data, Collections.EMPTY_MAP);
        }

        public State() {
            this(false, Optional.empty(), Collections.EMPTY_MAP);
        }

        public State<T> withActive() {
            return new State<>(true, current, validationErrors);
        }

        public State<T> withValue(T value) {
            return new State<T>(false, this.current.map(v -> new KeyedEntity<>(v.key, value)), this.validationErrors);
        }

        public State<T> withValidationErrors(Map<String, String> validationErrors) {
            return new State<T>(this.isActive, this.current, validationErrors);
        }
    }
}
