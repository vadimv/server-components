package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;
import rsp.server.Path;
import rsp.server.http.HttpStateOrigin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class StatefulComponentDefinition<S> implements SegmentDefinition {

    private final Object key;

    protected StatefulComponentDefinition(final Object key) {
        this.key = Objects.requireNonNull(key);
    }

    protected abstract Function<HttpStateOrigin, CompletableFuture<? extends S>> resolveStateFunction();

    protected abstract BiFunction<S, Path, Path> state2pathFunction();

    protected abstract ComponentView<S> componentView();

    @Override
    public boolean render(final RenderContext renderContext) {
        if (renderContext instanceof ComponentRenderContext componentRenderContext) {
            final Component<S> component = componentRenderContext.openComponent(key,
                                                                                resolveStateFunction(),
                                                                                state2pathFunction(),
                                                                                componentView());
            component.render(renderContext);

            componentRenderContext.closeComponent();
            return true;
        } else {
            return false;
        }
    }
}
