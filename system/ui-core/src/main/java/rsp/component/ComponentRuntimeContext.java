package rsp.component;

import java.util.Objects;

/** Immutable inputs available while creating one component segment runtime. */
public record ComponentRuntimeContext(ComponentCompositeKey componentId,
                                      TreeBuilderFactory treeBuilderFactory,
                                      ComponentContext componentContext,
                                      CommandsEnqueue commandsEnqueue) {
    public ComponentRuntimeContext {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(treeBuilderFactory, "treeBuilderFactory");
        Objects.requireNonNull(componentContext, "componentContext");
        Objects.requireNonNull(commandsEnqueue, "commandsEnqueue");
    }
}
