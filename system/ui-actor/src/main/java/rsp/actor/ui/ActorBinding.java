package rsp.actor.ui;

import rsp.actor.ActorDefinition;
import rsp.actor.runtime.ActorView;

import java.util.Objects;

/** Lazy attachment resolution; resolving a candidate must wait until its first real render. */
@FunctionalInterface
public interface ActorBinding<S, M> {
    ActorView<S, M> resolve(ActorComponentContext context);

    static <S, M> ActorBinding<S, M> existing(ActorView<S, M> view) {
        Objects.requireNonNull(view, "view");
        return _ -> view;
    }

    static <K, S, M> ActorBinding<S, M> page(
            PageActorDirectory<K, M> directory, ActorDefinition<S, M> definition) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(definition, "definition");
        return context -> context.in(directory).activate(definition).view();
    }
}
