package rsp.state;


import java.util.function.Function;

public interface FunctionConsumer<S> {
    void accept(Function<S, S> function);
}
