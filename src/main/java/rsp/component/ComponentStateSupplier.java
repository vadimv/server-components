package rsp.component;

import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface ComponentStateSupplier<S> {

    S getState(ComponentCompositeKey componentId, Function<String, Object> session);

}
