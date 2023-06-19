package rsp.component;

import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;
import rsp.stateview.ComponentView;
import rsp.stateview.NewState;

public final class ComponentDefinition<S> implements SegmentDefinition {
    private static final System.Logger logger = System.getLogger(ComponentDefinition.class.getName());

    private final ComponentView<S> componentView;

    private final S initialState;

    public ComponentDefinition(final S initialState,
                               final ComponentView<S> componentView) {

        this.initialState = initialState;
        this.componentView = componentView;
    }

    @Override
    public void render(final RenderContext renderContext) {
        final NewState<S> newStateHandler = renderContext.openComponent(initialState, componentView);

        final SegmentDefinition view = componentView.apply(initialState).apply(newStateHandler);

        view.render(renderContext);

        renderContext.closeComponent();
    }
}
