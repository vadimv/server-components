package rsp;

import rsp.html.DocumentPartDefinition;
import rsp.page.PageRenderContext;


public class StatefulComponent<S> implements DocumentPartDefinition {

    public final ComponentStateFunction<S> componentStateFunction;

    public StatefulComponent(ComponentStateFunction<S> componentStateFunction) {
        this.componentStateFunction = componentStateFunction;
    }

    @Override
    public void render(PageRenderContext renderContext) {
        System.out.println("accept:" + this);
    }
}
