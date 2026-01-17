package rsp.compositions;

import rsp.component.Lookup;

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
}
