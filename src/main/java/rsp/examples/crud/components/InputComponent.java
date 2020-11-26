package rsp.examples.crud.components;

import java.util.Optional;
import java.util.function.Function;

public interface InputComponent<S, T> extends FieldComponent<S> {
    Function<S, Optional<String>>[] validations();

    class State<S> {
        public final S value;
        public final Optional<String> validationErrorText;

        public State(S value, Optional<String> validationErrorText) {
            this.value = value;
            this.validationErrorText = validationErrorText;
        }
    }
}
