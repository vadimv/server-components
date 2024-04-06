package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;
import rsp.server.http.HttpStateOrigin;
import rsp.util.TriConsumer;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

public abstract class StatefulComponentDefinition<S> implements SegmentDefinition {

    private final Object key;

    public StatefulComponentDefinition(final Object key) {
        this.key = Objects.requireNonNull(key);
    }

    protected abstract Function<HttpStateOrigin, CompletableFuture<? extends S>> resolveStateFunction();

    protected abstract ComponentView<S> componentView();

    protected abstract BeforeRenderCallback<S> beforeRenderCallback();

    protected abstract StateAppliedCallback<S> newStateAppliedCallback();

    @Override
    public boolean render(final RenderContext renderContext) {
        if (renderContext instanceof ComponentRenderContext componentRenderContext) {
            final Component<S> component = componentRenderContext.openComponent(key,
                                                                                resolveStateFunction(),
                                                                                beforeRenderCallback(),
                                                                                componentView(),
                                                                                newStateAppliedCallback());
            component.render(componentRenderContext);

            componentRenderContext.closeComponent();
            return true;
        } else {
            return false;
        }
    }
}
