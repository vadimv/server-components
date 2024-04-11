package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;

import java.util.Objects;

public abstract class StatefulComponentDefinition<S> implements SegmentDefinition {

    private final Object componentType;

    protected StatefulComponentDefinition(final Object componentType) {
        this.componentType = Objects.requireNonNull(componentType);
    }

    protected abstract ComponentStateSupplier<S> stateSupplier();

    protected abstract ComponentView<S> componentView();

    protected abstract MountCallback<S> componentDidMount();

    protected abstract StateAppliedCallback<S> componentDidUpdate();

    protected abstract UnmountCallback<S> componentWillUnmount();

    @Override
    public boolean render(final RenderContext renderContext) {
        if (renderContext instanceof ComponentRenderContext componentRenderContext) {
            final Component<S> component = componentRenderContext.openComponent(componentType,
                                                                                stateSupplier(),
                                                                                componentDidMount(),
                                                                                componentView(),
                                                                                componentDidUpdate(),
                                                                                componentWillUnmount());
            component.render(componentRenderContext);

            componentRenderContext.closeComponent();
            return true;
        } else {
            return false;
        }
    }
}
