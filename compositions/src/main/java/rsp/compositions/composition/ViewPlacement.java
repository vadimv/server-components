package rsp.compositions.composition;

import rsp.component.Lookup;
import rsp.compositions.contract.ViewContract;

import java.util.Objects;
import java.util.function.Function;

/**
 * ViewPlacement - Associates a contract class with its factory.
 * <p>
 * The factory takes Lookup and produces a ViewContract instance.
 * This allows contracts to receive lookup via constructor injection.
 * The contractClass is used for routing and resolution.
 * <p>
 * Lifecycle (eager vs lazy) is derived from Router + Layout configuration,
 * not declared here. Visual positioning is owned by Layout implementations.
 */
public record ViewPlacement(Class<? extends ViewContract> contractClass,
                            Function<Lookup, ViewContract> contractFactory) {
    /**
     * Compact constructor with validation.
     */
    public ViewPlacement {
        Objects.requireNonNull(contractClass, "contractClass cannot be null");
        Objects.requireNonNull(contractFactory, "contractFactory cannot be null");
    }
}
