package rsp.component;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is used to pass information from components closer to a components tree root to their downstream components.
 * Values are added during an initial state rendering of an upstream component and stored with a key when their state is resolved.
 * This way every component down the hierarchy can add new key-values overriding existing with the same key.
 * These values can be accessed by downstream components by their keys.
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
     * Retrieves a value by a key.
     * @param name a key
     * @return a value
     */
    public Object getAttribute(final String name) {
        return attributes.get(name);
    }

    /**
     * Creates a new immutable instance with the map of values to pass to downstream components.
     * @param overlayAttributes a map with key-values
     * @return a new instance of ComponentContext
     */
    public ComponentContext with(final Map<String, Object> overlayAttributes) {
        final Map<String, Object> newAttributes = new HashMap<>(attributes);
        newAttributes.putAll(overlayAttributes);
        return new ComponentContext(newAttributes);
    }

}
