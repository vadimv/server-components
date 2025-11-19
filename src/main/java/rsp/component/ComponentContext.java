package rsp.component;

import java.util.HashMap;
import java.util.Map;

public final class ComponentContext {


    private final Map<String, Object> attributes;

    public ComponentContext() {
        this(new HashMap<>());
    }

    ComponentContext(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Object getAttribute(final String name) {
        return attributes.get(name);
    }

    public ComponentContext with(final Map<String, Object> overlayAttributes) {
        final Map<String, Object> newAttributes = new HashMap<>(attributes);
        newAttributes.putAll(overlayAttributes);
        return new ComponentContext(newAttributes);
    }

}
