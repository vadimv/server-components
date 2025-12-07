package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

/**
 * This class represents a unique component's instance identifier within a components tree and generally.
 * An instance of this class is intended to use as a key in a cache for saving a component state.
 * @param sessionId
 * @param componentType
 * @param componentPath
 */
public record ComponentCompositeKey(QualifiedSessionId sessionId, Object componentType, TreePositionPath componentPath) {}
