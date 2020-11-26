package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.useState;

public class Edit<T> implements Component<Edit.State<T>> {

    private final Function<UseState<T>, Form<T>> formFunction;
    public Edit(Function<UseState<T>, Form<T>> formFunction) {
        this.formFunction = formFunction;
    }


    @Override
    public DocumentPartDefinition render(UseState<Edit.State<T>> us) {
        return formFunction.apply(useState(() -> us.get().current.get().data,
                                            v -> us.accept(us.get().withValue(v))))
                                                   .render(useState(() -> new Form.State<>(us.get().current.map(v -> v.data)),
                                                                     v -> us.accept(new Edit.State<>()))); // TODO ??
    }

    public static class State<T> {
        public final boolean isActive;
        public final Optional<KeyedEntity<String, T>> current;
        public State(boolean isActive, Optional<KeyedEntity<String, T>> data) {
            this.isActive = isActive;
            this.current = data;
        }

        public State() {
            this(false, Optional.empty());
        }

        public State<T> withValue(T value) {
            return new State<T>(false, current.map(v -> new KeyedEntity<>(v.key, value)));
        }
    }
}
