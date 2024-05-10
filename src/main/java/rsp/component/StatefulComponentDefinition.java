package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.server.RemoteOut;
import rsp.server.http.PageStateOrigin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public abstract class StatefulComponentDefinition<S> implements SegmentDefinition, ComponentFactory<S> {

    protected final Object componentType;

    protected StatefulComponentDefinition(final Object componentType) {
        this.componentType = Objects.requireNonNull(componentType);
    }

    protected abstract ComponentStateSupplier<S> stateSupplier();

    protected abstract ComponentView<S> componentView();


    protected ComponentMountedCallback<S> componentDidMount() {
        return (key, state, newState) -> {};
    }

    protected ComponentUpdatedCallback<S> componentDidUpdate() {
        return (key, oldState, state, newState) -> {};
    }

    protected ComponentUnmountedCallback<S> componentWillUnmount() {
        return (key, state) -> {};
    }

    @Override
    public Component<S> createComponent(final QualifiedSessionId sessionId,
                                        final ComponentPath componentPath,
                                        final PageStateOrigin pageStateOrigin,
                                        final RenderContextFactory renderContextFactory,
                                        final RemoteOut remotePageMessagesOut,
                                        final Object sessionLock) {
        final ComponentCompositeKey key = new ComponentCompositeKey(sessionId, componentType, componentPath);
        final Supplier<CompletableFuture<? extends S>> resolveStateSupplier = () -> stateSupplier().getState(key,
                                                                                                             pageStateOrigin.httpStateOrigin());
        return new Component<>(key,
                               resolveStateSupplier,
                               componentView(),
                               new ComponentCallbacks<>(componentDidMount(),
                                                        componentDidUpdate(),
                                                        componentWillUnmount()),
                               renderContextFactory,
                               remotePageMessagesOut,
                               sessionLock);
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        final Component<S> component = renderContext.openComponent(this);
        component.render(renderContext);
        renderContext.closeComponent();
        return true;
    }
}
