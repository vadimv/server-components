package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;

import java.util.Objects;

public abstract class StatefulComponentDefinition<S> implements SegmentDefinition {

    private final Object key;

    public StatefulComponentDefinition(final Object key) {
        this.key = Objects.requireNonNull(key);
    }

    protected abstract ComponentStateSupplier<S> stateSupplier();

    protected abstract ComponentView<S> componentView();

    protected abstract BeforeRenderCallback<S> beforeRenderCallback();

    protected abstract StateAppliedCallback<S> afterStateAppliedCallback();

    protected abstract UnmountCallback<S> unmountCallback();

    @Override
    public boolean render(final RenderContext renderContext) {
        if (renderContext instanceof ComponentRenderContext componentRenderContext) {
            final Component<S> component = componentRenderContext.openComponent(key,
                                                                                stateSupplier(),
                                                                                beforeRenderCallback(),
                                                                                componentView(),
                                                                                afterStateAppliedCallback(),
                                                                                unmountCallback());
            component.render(componentRenderContext);

            componentRenderContext.closeComponent();
            return true;
        } else {
            return false;
        }
    }
}
