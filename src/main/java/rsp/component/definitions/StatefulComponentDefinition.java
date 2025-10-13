package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.TreePositionPath;
import rsp.html.SegmentDefinition;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.SessionEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

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
                                        final TreePositionPath componentPath,
                                        final RenderContextFactory renderContextFactory,
                                        final Map<String, Object> sessionObjects,
                                        final Consumer<SessionEvent> commandsEnqueue) {
        final ComponentCompositeKey key = new ComponentCompositeKey(sessionId, componentType, componentPath);
        return new Component<>(key,
                               stateSupplier(),
                               componentView(),
                               new ComponentCallbacks<>(componentDidMount(),
                                                        componentDidUpdate(),
                                                        componentWillUnmount()),
                               renderContextFactory,
                               sessionObjects,
                               commandsEnqueue);
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        final Component<S> component = renderContext.openComponent(this);
        component.render(renderContext);
        renderContext.closeComponent();
        return true;
    }
}
