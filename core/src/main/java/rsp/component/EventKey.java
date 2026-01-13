package rsp.component;

public sealed interface EventKey<T> permits EventKey.SimpleKey, EventKey.DynamicKey, EventKey.VoidKey {

    /**
     * Event name used for matching.
     */
    String name();

    /**
     * Runtime type of the payload.
     */
    Class<T> payloadType();

    /**
     * Simple event key with fixed name and payload type.
     */
    record SimpleKey<T>(String name, Class<T> payloadType) implements EventKey<T> {}

    /**
     * Dynamic event key for parameterized event families.
     * Example: "stateUpdated.*" for "stateUpdated.p", "stateUpdated.sort", etc.
     */
    record DynamicKey<T>(String baseName, Class<T> payloadType) implements EventKey<T> {
        @Override
        public String name() {
            return baseName + ".*";
        }

        /**
         * Create a specific event key from this dynamic key.
         */
        public SimpleKey<T> with(String extension) {
            return new SimpleKey<>(baseName + "." + extension, payloadType);
        }
    }

    /**
     * Void event key for events with no payload.
     */
    record VoidKey(String name) implements EventKey<Void> {
        @Override
        public Class<Void> payloadType() {
            return Void.class;
        }
    }
}