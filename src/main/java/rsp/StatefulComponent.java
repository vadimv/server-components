package rsp;

import rsp.html.DocumentPartDefinition;
import rsp.page.PageRenderContext;


public class StatefulComponent<S> implements DocumentPartDefinition {

    public final UseStateComponentFunction<S> useStateComponentFunction;

    public StatefulComponent(UseStateComponentFunction<S> useStateComponentFunction) {
        this.useStateComponentFunction = useStateComponentFunction;
    }

    @Override
    public void render(PageRenderContext renderContext) {
        System.out.println("accept:" + this);
    }
}
