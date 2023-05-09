package rsp;

import rsp.html.DocumentPartDefinition;
import rsp.page.PageRenderContext;


public class StatefulComponent<S> implements DocumentPartDefinition {

    public final StateView<S> stateView;

    public StatefulComponent(StateView<S> stateView) {
        this.stateView = stateView;
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        System.out.println("accept:" + this);
    }
}
