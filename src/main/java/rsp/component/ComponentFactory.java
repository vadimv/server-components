package rsp.component;

import rsp.dom.VirtualDomPath;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.server.RemoteOut;
import rsp.server.http.PageStateOrigin;

@FunctionalInterface
public interface ComponentFactory<S> {
    Component<S> createComponent(QualifiedSessionId sessionId,
                                 ComponentPath componentPath,
                                 PageStateOrigin pageStateOrigin,
                                 RenderContextFactory renderContextFactory,
                                 RemoteOut remotePageMessagesOut,
                                 Object sessionLock);
}
