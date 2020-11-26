package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
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
        return formFunction.apply(useState(() -> us.get().data.get(),
                                            v -> us.accept(State.withValue(v))))
                                                   .render(useState(() -> new Form.State<>(us.get().data),
                                                                     v -> us.accept(new Edit.State<>()))); // TODO ??
    }

    public static class State<T> {
        public final boolean isActive;
        public final Optional<T> data;
        public State(boolean isActive, Optional<T> data) {
            this.isActive = isActive;
            this.data = data;
        }

        public State() {
            this(false, Optional.empty());
        }

        public static <T> State<T> withValue(T value) {
            return new State<T>(false, Optional.of(value));
        }
    }
}
