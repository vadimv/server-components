package rsp.component;

import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.page.EventContext;
import rsp.page.PageRenderContext;
import rsp.ref.Ref;
import rsp.server.InMessages;
import rsp.server.OutMessages;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class DefaultComponentRenderContext implements ComponentRenderContext {
    private final PageRenderContext context;
    private final OutMessages out;

    public DefaultComponentRenderContext(final PageRenderContext context, final OutMessages out) {
        this.context = context;
        this.out = out;
    }

    @Override
    public ComponentRenderContext newInstance() {
        return new DefaultComponentRenderContext(context.newInstance(), out);
    }


    @Override
    public OutMessages out() {
        return out;
    }

    @Override
    public Map<Event.Target, Event> events() {
        return context.events();
    }

    @Override
    public Map<Ref, VirtualDomPath> refs() {
        return context.refs();
    }

    @Override
    public void setStatusCode(int statusCode) {
        context.setStatusCode(statusCode);
    }

    @Override
    public void setHeaders(Map<String, String> headers) {
        context.setHeaders(headers);
    }

    @Override
    public void setDocType(String docType) {
        context.setDocType(docType);
    }

    @Override
    public void openNode(XmlNs xmlns, String name) {
        context.openNode(xmlns, name);
    }

    @Override
    public void closeNode(String name, boolean upgrade) {
        context.closeNode(name, upgrade);
    }

    @Override
    public void setAttr(XmlNs xmlNs, String name, String value, boolean isProperty) {
        context.setAttr(xmlNs, name, value, isProperty);
    }

    @Override
    public void setStyle(String name, String value) {
        context.setStyle(name, value);
    }

    @Override
    public void addTextNode(String text) {
        context.addTextNode(text);
    }

    @Override
    public void addEvent(Optional<VirtualDomPath> elementPath,
                         String eventName,
                         Consumer<EventContext> eventHandler,
                         boolean preventDefault,
                         Event.Modifier modifier) {

        context.addEvent(elementPath,
                         eventName,
                         eventHandler,
                         preventDefault,
                         modifier);
    }

    @Override
    public void addRef(Ref ref) {
        context.addRef(ref);
    }

    @Override
    public Tag tag() {
        return context.tag();
    }
}
