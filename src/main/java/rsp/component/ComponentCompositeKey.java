package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.Objects;

/**
 * This class represents a unique component's instance identifier within a components tree and generally.
 * An instance of this class is intended to use as a key in a cache for saving a component state.
 * @param sessionId a session identifier, must not be null
 * @param componentType an object that identifies this component's type, must not be null
 * @param componentPath a path to this component in the components tree, must not be null
 */
public record ComponentCompositeKey(QualifiedSessionId sessionId, Object componentType, TreePositionPath componentPath) {
    public ComponentCompositeKey {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(componentType);
        Objects.requireNonNull(componentPath);
    }
}
