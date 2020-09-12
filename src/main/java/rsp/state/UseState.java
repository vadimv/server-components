package rsp.state;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface UseState<S> extends Supplier<S>, Consumer<S> {

}
