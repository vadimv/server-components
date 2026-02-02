package rsp.compositions.contract;

import java.util.HashMap;
import java.util.Map;

/**
 * ActionBindings - Maps abstract action names to target contract classes.
 * <p>
 * Views emit abstract actions (e.g., "edit", "create") via ACTION events.
 * Contracts declare bindings that translate these abstract actions to
 * concrete SHOW events targeting specific contract classes.
 * <p>
 * This decouples Views from concrete Contracts - Views don't know about
 * PostEditContract.class, they just emit ACTION("edit", {id: "123"}).
 * <p>
 * Example usage in a ListViewContract:
 * <pre>
 * {@code
 * @Override
 * protected ActionBindings actionBindings() {
 *     return ActionBindings.builder()
 *         .bind("edit", PostEditContract.class)
 *         .bind("create", PostCreateContract.class)
 *         .build();
 * }
 * }
 * </pre>
 */
public class ActionBindings {
    
    /**
     * ShowPayload - Data emitted with SHOW events.
     * <p>
     * Contracts emit SHOW events to trigger on-demand instantiation of other contracts.
     * SceneComponent handles these events and instantiates the target contract.
     *
     * @param contractClass The contract class to instantiate and show
     * @param data Data to pass to the contract (e.g., {id: "123"} for edit)
     */
    public record ShowPayload(
        Class<? extends ViewContract> contractClass,
        Map<String, Object> data
    ) {}
}
