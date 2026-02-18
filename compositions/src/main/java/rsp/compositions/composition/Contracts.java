package rsp.compositions.composition;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ViewContract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Per-composition binding of contract types to their factories and views.
 * <p>
 * Each {@link #bind} call declares a 1-1-1 relationship: contract type, its constructor,
 * and its view. All three must be present — partial bindings are not possible.
 */
public class Contracts {

    private final Map<Class<? extends ViewContract>, Supplier<? extends Component<?>>> views;
    private final Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> contractFactories;

    public Contracts() {
        this.views = new LinkedHashMap<>();
        this.contractFactories = new LinkedHashMap<>();
    }

    /**
     * Bind a contract type to its factory and view factory.
     *
     * @param contractClass   The concrete contract class
     * @param contractFactory Factory that takes a Lookup and produces a ViewContract
     * @param viewFactory     Factory that creates the UI component for this contract
     * @return this for chaining
     */
    public Contracts bind(Class<? extends ViewContract> contractClass,
                          Function<Lookup, ViewContract> contractFactory,
                          Supplier<? extends Component<?>> viewFactory) {
        contractFactories.put(contractClass, contractFactory);
        views.put(contractClass, viewFactory);
        return this;
    }

    /**
     * Resolve the UI component for the given contract class.
     *
     * @param contractClass The contract class to resolve
     * @return The UI component instance
     * @throws IllegalStateException if no view factory is bound for this class
     */
    public Component<?> resolveView(Class<? extends ViewContract> contractClass) {
        Supplier<? extends Component<?>> factory = views.get(contractClass);
        if (factory == null) {
            throw new IllegalStateException(
                    "No UI component bound for contract: " + contractClass.getName());
        }
        return factory.get();
    }

    /**
     * Returns the contract factory for the given class, or null if not bound.
     *
     * @param contractClass The contract class
     * @return The factory, or null
     */
    public Function<Lookup, ViewContract> contractFactory(Class<? extends ViewContract> contractClass) {
        return contractFactories.get(contractClass);
    }

    /**
     * Returns all bound contract classes in insertion order.
     *
     * @return unmodifiable set of contract classes
     */
    public Set<Class<? extends ViewContract>> contractClasses() {
        return Collections.unmodifiableSet(contractFactories.keySet());
    }
}
