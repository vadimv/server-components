package rsp.component;

import rsp.page.QualifiedSessionId;

public record ComponentCompositeKey(QualifiedSessionId sessionId, Object componentType, ComponentPath path) {
}
