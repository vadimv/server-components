package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.server.RemoteOut;

@FunctionalInterface
public interface ComponentFactory<S> {
    Component<S> createComponent(QualifiedSessionId sessionId,
                                 TreePositionPath componentPath,
                                 RenderContextFactory renderContextFactory,
                                 RemoteOut remotePageMessagesOut,
                                 Object sessionLock);
}
