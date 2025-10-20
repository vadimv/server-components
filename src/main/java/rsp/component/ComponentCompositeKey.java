package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

/**
 * This class represents an unique component's instance identifier.
 * @param sessionId
 * @param componentType
 * @param componentPath
 */
public record ComponentCompositeKey(QualifiedSessionId sessionId, Object componentType, TreePositionPath componentPath) {}
