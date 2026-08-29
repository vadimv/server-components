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
public record BlockDescriptor(Object blockKey,
                              Class<? extends Block<?, ?>> blockClass,
                              Map<String, Object> showData,
                              long instanceId) {
    private static final AtomicLong NEXT_INSTANCE_ID = new AtomicLong();

    public BlockDescriptor {
        Objects.requireNonNull(blockKey, "blockKey");
        Objects.requireNonNull(blockClass, "blockClass");
        showData = showData == null || showData.isEmpty() ? Map.of() : Map.copyOf(showData);
    }

    public BlockDescriptor(Class<? extends Block<?, ?>> blockClass,
                           Map<String, Object> showData,
                           long instanceId) {
        this(blockClass, blockClass, showData, instanceId);
    }

    public static BlockDescriptor forBlock(Class<? extends Block<?, ?>> blockClass,
                                           Map<String, Object> showData) {
        return forBlock(blockClass, blockClass, showData);
    }

    public static BlockDescriptor forBlock(Object blockKey,
                                           Class<? extends Block<?, ?>> blockClass,
                                           Map<String, Object> showData) {
        return new BlockDescriptor(blockKey, blockClass, showData, NEXT_INSTANCE_ID.incrementAndGet());
    }

    public static BlockDescriptor forTarget(BlockTarget target, Map<String, Object> showData) {
        Objects.requireNonNull(target, "target");
        return forBlock(target.key(), target.blockClass(), showData);
    }

    public BlockTarget target() {
        return new BlockTarget(blockKey, blockClass);
    }

}
