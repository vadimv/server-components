package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.Command;


import java.util.function.Consumer;

@FunctionalInterface
public interface ComponentFactory<S> {
    /**
     *
     * @param sessionId
     * @param componentPath
     * @param renderContextFactory
     * @param componentContext
     * @param commandsEnqueue
     * @return
     */
    ComponentSegment<S> createComponent(QualifiedSessionId sessionId,
                                        TreePositionPath componentPath,
                                        RenderContextFactory renderContextFactory,
                                        ComponentContext componentContext,
                                        Consumer<Command> commandsEnqueue);
}
