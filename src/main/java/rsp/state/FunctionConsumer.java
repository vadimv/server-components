package rsp.state;

import java.util.Optional;
import java.util.function.Function;

public interface FunctionConsumer<S> {
    void accept(Function<S, S> function);

    void acceptOptional(Function<S, Optional<S>> function);
}
