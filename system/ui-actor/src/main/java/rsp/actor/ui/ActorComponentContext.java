package rsp.actor.ui;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentContext;
import rsp.page.PageScope;
import rsp.page.QualifiedSessionId;

import java.util.Objects;

/** Host-selection inputs for one actor-backed component segment. */
public record ActorComponentContext(ComponentCompositeKey componentId,
                                    ComponentContext componentContext,
                                    CommandsEnqueue commandsEnqueue) {
    public ActorComponentContext {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(componentContext, "componentContext");
        Objects.requireNonNull(commandsEnqueue, "commandsEnqueue");
    }

    public QualifiedSessionId pageId() {
        return componentId.sessionId();
    }

    public PageScope pageScope() {
        return componentContext.getRequired(PageScope.class);
    }

    /** Places this component's actor in a discoverable, page-owned directory. */
    public <K, M> PageActorPlacement<M> in(PageActorDirectory<K, M> directory) {
        return new PageActorPlacement<>(directory, pageId(), pageScope(), commandsEnqueue);
    }
}
