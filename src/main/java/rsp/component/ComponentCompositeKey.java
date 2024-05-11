package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

public record ComponentCompositeKey(QualifiedSessionId sessionId, Object componentType, TreePositionPath componentPath) {}
