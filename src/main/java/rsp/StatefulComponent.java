package rsp;

import rsp.html.DocumentPartDefinition;
import rsp.page.PageRenderContext;
import rsp.page.StateNotificationListener;


public class StatefulComponent<S> implements DocumentPartDefinition {

    public volatile S state;
    private volatile StateNotificationListener stateUpdateListener;
    public final ComponentStateFunction<S> componentStateFunction;

    public StatefulComponent(S initialState,
                             ComponentStateFunction<S> componentStateFunction) {
        this.state = initialState;
        this.componentStateFunction = componentStateFunction;
    }

    @Override
    public void render(PageRenderContext renderContext) {
        stateUpdateListener = renderContext.getStateNotificationListener();
        final DocumentPartDefinition documentPartDefinition = componentStateFunction.apply(state, s -> {
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
