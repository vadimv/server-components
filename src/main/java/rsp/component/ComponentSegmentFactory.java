package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;


import java.util.function.Consumer;

/**
 * Defines an abstraction for creating instances of ComponentSegment class
 * @param <S> a component's state type
 */
@FunctionalInterface
public interface ComponentSegmentFactory<S> {

    /**
     * Creates a new instance of a component.
     * @param sessionId a current session identifier, must not be null
     * @param componentPath a position path of this component, must not be null
     * @param treeBuilderFactory an instance of a factory for new tree builders, must not be null
     * @param componentContext a components' context passed from components up in the tree hierarchy, must not be null
     * @param commandsEnqueue a sink for commands, must not be null
     * @return a new instance of a component
     */
    ComponentSegment<S> createComponentSegment(QualifiedSessionId sessionId,
                                               TreePositionPath componentPath,
                                               TreeBuilderFactory treeBuilderFactory,
                                               ComponentContext componentContext,
                                               Consumer<Command> commandsEnqueue);
}
