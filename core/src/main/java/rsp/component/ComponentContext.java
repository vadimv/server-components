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
 *
 * <p>By default, the context is propagated down the component tree. However, a component can choose to
 * isolate its subtree by creating a new, empty {@code ComponentContext} instead of extending the parent's context.</p>
 *
 * <p><strong>Security Notice:</strong> This context may contain sensitive information, such as session identifiers
 * or device IDs. By design, this information is accessible to all components in the subtree.
 * It is assumed that all components running on the server are trusted. Developers must ensure that
 * sensitive context values are not accidentally rendered into the client-side HTML or leaked to untrusted parties.</p>
 */
public final class ComponentContext {

    public static final String DEVICE_ID_KEY = "deviceId";
    public static final String SESSION_ID_KEY = "sessionId";

    private final Map<String, Object> attributes;

    /**
     * Creates a new, empty component context.
     * This constructor is typically used to create a root context or to isolate a subtree
     * from the parent context, effectively clearing all upstream attributes.
     * @see #with(Map)
     */
    public ComponentContext() {
        this(new HashMap<>());
    }

    private ComponentContext(final Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Retrieves an attribute's value by a key.
     * @param name a key, must not be null
     * @return a value, or null if the key is not present
     */
    public Object getAttribute(final String name) {
        Objects.requireNonNull(name);
        return attributes.get(name);
    }

    /**
     * Creates a new immutable instance with the map of values to pass to downstream components.
     * @param overlayAttributes a map with key-values, must not be null
     * @return a new instance of ComponentContext
     * @param <T> the type of values in the overlay map
     */
    public <T> ComponentContext with(final Map<String, T> overlayAttributes) {
        Objects.requireNonNull(overlayAttributes);
        final Map<String, Object> newAttributes = new HashMap<>(attributes);
        newAttributes.putAll(overlayAttributes);
        return new ComponentContext(newAttributes);
    }

    /**
     * Gets the device ID from the context.
     * @return the device ID, or null if not present
     */
    public String deviceId() {
        return (String) attributes.get(DEVICE_ID_KEY);
    }

    /**
     * Gets the session ID from the context.
     * @return the session ID, or null if not present
     */
    public String sessionId() {
        return (String) attributes.get(SESSION_ID_KEY);
    }
}
