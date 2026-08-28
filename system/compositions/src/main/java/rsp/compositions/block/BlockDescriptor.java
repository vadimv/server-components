package rsp.compositions.block;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable scene data that selects one block component instance.
 *
 * A descriptor deliberately contains no live block or lookup. The component
 * tree creates the runtime only when this descriptor's branch is mounted.
 */
public record BlockDescriptor(Class<? extends Block<?, ?>> blockClass,
                                 Map<String, Object> showData,
                                 long instanceId) {
    private static final AtomicLong NEXT_INSTANCE_ID = new AtomicLong();

    public BlockDescriptor {
        Objects.requireNonNull(blockClass, "blockClass");
        showData = showData == null || showData.isEmpty() ? Map.of() : Map.copyOf(showData);
    }

    public static BlockDescriptor forBlock(Class<? extends Block<?, ?>> blockClass,
                                                 Map<String, Object> showData) {
        return new BlockDescriptor(blockClass, showData, NEXT_INSTANCE_ID.incrementAndGet());
    }

}
