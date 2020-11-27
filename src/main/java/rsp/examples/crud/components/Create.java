package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class Create<T> implements Component<Create.State<T>> {

    private final Function<UseState<T>, Form<T>> formFunction;
    public Create(Function<UseState<T>, Form<T>> formFunction) {
        this.formFunction = formFunction;
    }


    @Override
    public DocumentPartDefinition render(UseState<Create.State<T>> us) {
        return div(span("Create"),
                   formFunction.apply(useState(() -> us.get().current.get(),
                                            v -> us.accept(us.get().withValue(v))))
                                                   .render(useState(() -> new Form.State<>(),
                                                                     v -> us.accept(new Create.State<>())))); // TODO ??
    }

    public static class State<T> {
        public final boolean isActive;
        public final Optional<T> current;
        public State(boolean isActive, Optional<T> data) {
            this.isActive = isActive;
            this.current = data;
        }

        public State() {
            this(false, Optional.empty());
        }

        public State<T> withValue(T value) {
            return new State<T>(false, Optional.of(value));
        }
    }
}
