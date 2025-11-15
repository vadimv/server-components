package rsp.component;

import rsp.page.Lookup;

import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface ComponentStateSupplier<S> {

    S getState(ComponentContext componentContext);

}
