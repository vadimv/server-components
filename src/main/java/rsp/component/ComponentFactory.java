package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.Lookup;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.SessionEvent;


import java.util.function.Consumer;

@FunctionalInterface
public interface ComponentFactory<S> {
    Component<S> createComponent(QualifiedSessionId sessionId,
                                 TreePositionPath componentPath,
                                 RenderContextFactory renderContextFactory,
                                 Lookup sessionObjects,
                                 Consumer<SessionEvent> commandsEnqueue);
}
