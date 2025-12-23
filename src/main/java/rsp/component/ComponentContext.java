package rsp.component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * This class is used to pass information from upstream components in a tree to their downstream components.
 * Values are added during an initial state rendering by an component and stored with a key when after its state is resolved.
 * This way every component down the hierarchy can add new key-values overriding existing with the same key.
 * These values can be accessed by downstream components by their string keys.
 * It might be useful depending on the requirements to have attribute values as JSON objects or to be typed.
 */
public final class ComponentContext {

    private final Map<String, Object> attributes;

    public ComponentContext() {
        this(new HashMap<>());
    }

    private ComponentContext(final Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Retrieves an attribute's value by a key.
     * @param name a key, must not be null
     * @return a value
     */
    public Object getAttribute(final String name) {
        Objects.requireNonNull(name);
        return attributes.get(name);
    }

    /**
     * Creates a new immutable instance with the map of values to pass to downstream components.
     * @param overlayAttributes a map with key-values, must not be null
     * @return a new instance of ComponentContext
     */
    public ComponentContext with(final Map<String, Object> overlayAttributes) {
        Objects.requireNonNull(overlayAttributes);
        final Map<String, Object> newAttributes = new HashMap<>(attributes);
        newAttributes.putAll(overlayAttributes);
        return new ComponentContext(newAttributes);
    }

}
