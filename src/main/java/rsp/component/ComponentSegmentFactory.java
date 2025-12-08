package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.Command;


import java.util.function.Consumer;

/**
 * Defines an abstraction for creating instances of ComponentSegment class
 * @param <S> a type for this component's state snapshot
 */
@FunctionalInterface
public interface ComponentSegmentFactory<S> {

    /**
     * Creates a new instance.
     * @param sessionId
     * @param componentPath
     * @param renderContextFactory
     * @param componentContext
     * @param commandsEnqueue
     * @return
     */
    ComponentSegment<S> createComponentSegment(QualifiedSessionId sessionId,
                                               TreePositionPath componentPath,
                                               RenderContextFactory renderContextFactory,
                                               ComponentContext componentContext,
                                               Consumer<Command> commandsEnqueue);
}
