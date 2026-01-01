package rsp.compositions;

import rsp.component.ComponentContext;

import java.util.function.Function;

/**
 * ViewPlacement - Associates a UI slot with a contract factory.
 * <p>
 * The factory takes ComponentContext and produces a ViewContract instance.
 * This allows contracts to receive context via constructor injection.
 * The contractClass is used for routing and resolution.
 */
public record ViewPlacement(Slot slot,
                            Class<? extends ViewContract> contractClass,
                            Function<ComponentContext, ViewContract> contractFactory) {
}
