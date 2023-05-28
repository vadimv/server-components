package rsp;

import rsp.html.DocumentPartDefinition;
import rsp.page.PageRenderContext;
import rsp.page.StateNotificationListener;


public final class StatefulComponent<S> implements DocumentPartDefinition {

    public final CreateViewFunction<S> createViewFunction;

    public volatile S state;

    private volatile StateNotificationListener stateUpdateListener;

    public StatefulComponent(final S initialState,
                             final CreateViewFunction<S> createViewFunction) {
        this.state = initialState;
        this.createViewFunction = createViewFunction;
    }

    @Override
    public void render(final PageRenderContext renderContext) {
        stateUpdateListener = renderContext.getStateNotificationListener();
        final DocumentPartDefinition documentPartDefinition = createViewFunction.apply(state, s -> {
            state = s;
            if (stateUpdateListener != null)
            {
                stateUpdateListener.notifyStateUpdate();
            }
            else
            {
                throw new IllegalStateException();
            }
        });
        documentPartDefinition.render(renderContext);
    }
}
