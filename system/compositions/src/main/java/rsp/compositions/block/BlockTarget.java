package rsp.compositions.block;

import java.util.Objects;

/**
 * A configured block target.
 *
 * <p>The key identifies one binding while the class describes its Java type.
 * For the compact class-based DSL the key and class are the same object.</p>
 */
public record BlockTarget(Object key, Class<? extends Block<?, ?>> blockClass) {
    public BlockTarget {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(blockClass, "blockClass");
    }
}
