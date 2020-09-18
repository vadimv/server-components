package rsp;

import rsp.dsl.RefDefinition;

import java.util.function.Consumer;

public class DelegatingRenderContext<S> implements RenderContext<S> {

    private final RenderContext[] contexts;

    public DelegatingRenderContext(RenderContext... contexts) {
        this.contexts = contexts;
    }

    @Override
    public void openNode(XmlNs xmlns, String name) {
        for(RenderContext rc: contexts) {
            rc.openNode(xmlns, name);
        }
    }

    @Override
    public void closeNode(String name) {
        for(RenderContext rc: contexts) {
            rc.closeNode(name);
        }
    }

    @Override
    public void setAttr(XmlNs xmlNs, String name, String value) {
        for(RenderContext rc: contexts) {
            rc.setAttr(xmlNs, name, value);
        }
    }

    @Override
    public void setStyle(String name, String value) {
        for(RenderContext rc: contexts) {
            rc.setStyle(name, value);
        }
    }

    @Override
    public void addTextNode(String text) {
        for(RenderContext rc: contexts) {
            rc.addTextNode(text);
        }
    }

    @Override
    public void addEvent(String eventName, Consumer<EventContext> eventHandler) {
        for(RenderContext rc: contexts) {
            rc.addEvent(eventName, eventHandler);
        }
    }

    @Override
    public void addRef(Ref ref) {
        for(RenderContext rc: contexts) {
            rc.addRef(ref);
        }
    }
}
