package rsp.compositions;

/**
 * Re-export of {@link rsp.component.ContextKey} for backward compatibility.
 * This allows existing code in the compositions module to continue using
 * the fully qualified name rsp.compositions.ContextKey.
 *
 * <p>New code should import from rsp.component.ContextKey instead.</p>
 *
 * @param <T> the type of value stored under this key
 * @deprecated Use {@link rsp.component.ContextKey} instead
 */
@Deprecated
public sealed interface ContextKey<T>
        permits ContextKey.ClassKey, ContextKey.StringKey, ContextKey.DynamicKey {

    Class<T> type();

    /**
     * @deprecated Use {@link rsp.component.ContextKey.ClassKey} instead
     */
    @Deprecated
    record ClassKey<T>(Class<T> clazz) implements ContextKey<T> {
        @Override
        public Class<T> type() {
            return clazz;
        }
    }

    /**
     * @deprecated Use {@link rsp.component.ContextKey.StringKey} instead
     */
    @Deprecated
    record StringKey<T>(String key, Class<T> type) implements ContextKey<T> {
    }

    /**
     * @deprecated Use {@link rsp.component.ContextKey.DynamicKey} instead
     */
    @Deprecated
    record DynamicKey<T>(String baseKey, Class<T> type) implements ContextKey<T> {
        public DynamicKey<T> with(String extension) {
            return new DynamicKey<>(baseKey + "." + extension, type);
        }
    }
}
