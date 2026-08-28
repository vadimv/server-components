package rsp.compositions.block;

import java.util.Map;
import java.util.Objects;

/**
 * ActionBindings - Maps abstract action names to target block classes.
 * <p>
 * Views emit abstract actions (e.g., "edit", "create") via ACTION events.
 * Blocks declare bindings that translate these abstract actions to
 * concrete SHOW events targeting specific block classes.
 * <p>
 * This decouples Views from concrete Blocks - Views don't know about
 * PostEditBlock.class, they just emit ACTION("edit", {id: "123"}).
 * <p>
 * Example usage in a list block component:
 * <pre>
 * {@code
 * @Override
 * protected ActionBindings actionBindings() {
 *     return ActionBindings.builder()
 *         .bind("edit", PostEditBlock.class)
 *         .bind("create", PostCreateBlock.class)
 *         .build();
 * }
 * }
 * </pre>
 */
public class ActionBindings {
    
    /**
     * ShowPayload - Data emitted with SHOW events.
     * <p>
     * Blocks emit SHOW events to trigger on-demand instantiation of other blocks.
     * Scene event handlers select a descriptor; the target block host
     * instantiates the block when its branch mounts.
     *
     * @param blockClass The block class to instantiate and show
     * @param data Data to pass to the block (e.g., {id: "123"} for edit)
     */
    public record ShowPayload(Class<? extends Block<?, ?>> blockClass, Map<String, Object> data) {
        public ShowPayload {
            Objects.requireNonNull(blockClass, "blockClass");
            Objects.requireNonNull(data, "data");
            data = Map.copyOf(data);
        }
    }
}
