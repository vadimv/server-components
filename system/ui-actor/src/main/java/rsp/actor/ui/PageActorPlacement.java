package rsp.actor.ui;

import rsp.actor.ActorDefinition;
import rsp.component.CommandsEnqueue;
import rsp.page.PageScope;
import rsp.page.QualifiedSessionId;

import java.util.Objects;

/** A resolved page host and identity policy for one actor component. */
public final class PageActorPlacement<M> {
    private final PageActorDirectory<?, M> directory;
    private final QualifiedSessionId pageId;
    private final PageScope pageScope;
    private final CommandsEnqueue commandsEnqueue;

    PageActorPlacement(PageActorDirectory<?, M> directory,
                       QualifiedSessionId pageId,
                       PageScope pageScope,
                       CommandsEnqueue commandsEnqueue) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.pageId = Objects.requireNonNull(pageId, "pageId");
        this.pageScope = Objects.requireNonNull(pageScope, "pageScope");
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue, "commandsEnqueue");
    }

    <S> PageActorHandle<S, M> activate(ActorDefinition<S, M> definition) {
        return directory.activate(pageId, pageScope, commandsEnqueue, definition);
    }
}
