package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

/**
 * This class represents a unique component's instance identifier within a components tree and generally.
 * An instance of this class is intended to use as a key in a cache for saving a component state.
 * @param sessionId a session identifier
 * @param componentType an object that identifies this component's type
 * @param componentPath a path to this component in the components tree
 */
public record ComponentCompositeKey(QualifiedSessionId sessionId, Object componentType, TreePositionPath componentPath) {}
