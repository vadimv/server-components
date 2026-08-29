package rsp.compositions.block;

import java.util.Map;
import java.util.Objects;

/**
 * ActionBindings - Maps abstract action names to target blocks.
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
     * @param blockKey The configured binding key to instantiate and show
     * @param data Data to pass to the block (e.g., {id: "123"} for edit)
     */
    public record ShowPayload(Object blockKey, Map<String, Object> data) {
        public ShowPayload {
            Objects.requireNonNull(blockKey, "blockKey");
            Objects.requireNonNull(data, "data");
            data = Map.copyOf(data);
        }

        /** Compatibility accessor for class-keyed payloads. */
        @SuppressWarnings("unchecked")
        public Class<? extends Block<?, ?>> blockClass() {
            if (!(blockKey instanceof Class<?> type) || !Block.class.isAssignableFrom(type)) {
                throw new IllegalStateException("Show target is not class-keyed: " + blockKey);
            }
            return (Class<? extends Block<?, ?>>) type;
        }
    }
}
