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
 * Per-composition registry holding contract factories and view factories.
 */
public class UiRegistry {

    private final Map<Class<? extends ViewContract>, Supplier<? extends Component<?>>> views;
    private final Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> contracts;

    public UiRegistry() {
        this.views = new LinkedHashMap<>();
        this.contracts = new LinkedHashMap<>();
    }

    /**
     * Register a contract factory and its view factory.
     *
     * @param contractClass   The concrete contract class
     * @param contractFactory Factory that takes a Lookup and produces a ViewContract
     * @param viewFactory     Factory that creates the UI component for this contract
     * @return this for chaining
     */
    public UiRegistry register(Class<? extends ViewContract> contractClass,
                               Function<Lookup, ViewContract> contractFactory,
                               Supplier<? extends Component<?>> viewFactory) {
        contracts.put(contractClass, contractFactory);
        views.put(contractClass, viewFactory);
        return this;
    }

    /**
     * Resolve the UI component for the given contract class.
     *
     * @param contractClass The contract class to resolve
     * @return The UI component instance
     * @throws IllegalStateException if no view factory is registered for this class
     */
    public Component<?> resolveView(Class<? extends ViewContract> contractClass) {
        Supplier<? extends Component<?>> factory = views.get(contractClass);
        if (factory == null) {
            throw new IllegalStateException(
                    "No UI component registered for contract: " + contractClass.getName());
        }
        return factory.get();
    }

    /**
     * Returns the contract factory for the given class, or null if not registered.
     *
     * @param contractClass The contract class
     * @return The factory, or null
     */
    public Function<Lookup, ViewContract> contractFactory(Class<? extends ViewContract> contractClass) {
        return contracts.get(contractClass);
    }

    /**
     * Returns all registered contract classes in insertion order.
     *
     * @return unmodifiable set of contract classes
     */
    public Set<Class<? extends ViewContract>> contractClasses() {
        return Collections.unmodifiableSet(contracts.keySet());
    }
}
