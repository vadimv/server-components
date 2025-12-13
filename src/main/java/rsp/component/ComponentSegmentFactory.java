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
     * @param sessionId a current session identifier
     * @param componentPath a position path of this component
     * @param treeBuilderFactory an instance of a factory for new tree builders
     * @param componentContext a components' context passed from compoenents up in the tree hierarchy
     * @param commandsEnqueue a sink for commands 
     * @return a new instance of a component
     */
    ComponentSegment<S> createComponentSegment(QualifiedSessionId sessionId,
                                               TreePositionPath componentPath,
                                               TreeBuilderFactory treeBuilderFactory,
                                               ComponentContext componentContext,
                                               Consumer<Command> commandsEnqueue);
}
