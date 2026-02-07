package rsp.compositions.contract;

import rsp.component.definitions.Component;
import rsp.compositions.composition.UiRegistry;

/**
 * Resolves ViewContract classes to their UI component implementations.
 * Walks up the class hierarchy to find a registered UI component,
 * supporting inheritance-based registration.
 */
public final class UiComponentResolver {
    private UiComponentResolver() {}

    /**
     * Resolves a ViewContract class to its UI implementation.
     * Walks up the class hierarchy to find a registered UI component.
     *
     * @throws IllegalStateException if no UI component is registered for the contract
     *         or any of its superclasses up to ViewContract
     */
    public static Component<?> resolve(UiRegistry uiRegistry,
                                       Class<? extends ViewContract> contractClass) {
        // Try the contract class itself first
        Component<?> uiComponent = uiRegistry.resolve(contractClass);
        if (uiComponent != null) {
            return uiComponent;
        }

        // Walk up the inheritance hierarchy to find a registered base class
        Class<?> current = contractClass.getSuperclass();
        while (current != null && ViewContract.class.isAssignableFrom(current)) {
            @SuppressWarnings("unchecked")
            Class<? extends ViewContract> baseClass = (Class<? extends ViewContract>) current;
            uiComponent = uiRegistry.resolve(baseClass);
            if (uiComponent != null) {
                return uiComponent;
            }
            current = current.getSuperclass();
        }

        throw new IllegalStateException("No UI component registered for contract: " + contractClass.getName());
    }
}
