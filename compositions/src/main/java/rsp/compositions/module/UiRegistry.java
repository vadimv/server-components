package rsp.compositions.module;

import rsp.component.definitions.Component;
import rsp.compositions.contract.ViewContract;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * UiRegistry - Maps ViewContract classes to UI component factories.
 * <p>
 * This registry allows runtime discovery of UI implementations.
 * UI components read data from ComponentContext, so factories don't need parameters.
 */
public class UiRegistry {
    private final Map<Class<? extends ViewContract>, Supplier<? extends Component<?>>> components = new HashMap<>();

    /**
     * Registers a UI component factory for a ViewContract class.
     *
     * @param contractClass The ViewContract class (e.g., ListViewContract.class)
     * @param componentFactory Factory that creates the UI component (e.g., DefaultListView::new)
     */
    public UiRegistry register(Class<? extends ViewContract> contractClass,
                               Supplier<? extends Component<?>> componentFactory) {
        components.put(contractClass, componentFactory);
        return this;
    }

    /**
     * Resolves a ViewContract class to its UI component instance.
     *
     * @param contractClass The ViewContract class to resolve
     * @return The UI component instance, or null if not registered
     */
    public Component<?> resolve(Class<? extends ViewContract> contractClass) {
        Supplier<? extends Component<?>> factory = components.get(contractClass);
        if (factory == null) {
            return null;
        }
        return factory.get();
    }
}
