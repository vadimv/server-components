package rsp.compositions.module;

import rsp.component.Lookup;
import rsp.compositions.contract.ViewContract;

import java.util.Objects;
import java.util.function.Function;

/**
 * ViewPlacement - Associates a UI slot with a contract factory.
 * <p>
 * The factory takes Lookup and produces a ViewContract instance.
 * This allows contracts to receive lookup via constructor injection.
 * The contractClass is used for routing and resolution.
 */
public record ViewPlacement(Slot slot,
                            Class<? extends ViewContract> contractClass,
                            Function<Lookup, ViewContract> contractFactory) {
    /**
     * Compact constructor with validation.
     */
    public ViewPlacement {
        Objects.requireNonNull(slot, "slot cannot be null");
        Objects.requireNonNull(contractClass, "contractClass cannot be null");
        Objects.requireNonNull(contractFactory, "contractFactory cannot be null");
    }
}
