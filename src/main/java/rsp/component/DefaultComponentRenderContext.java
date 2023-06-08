package rsp.component;

import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.page.EventContext;
import rsp.page.LivePage;
import rsp.page.PageRenderContext;
import rsp.ref.Ref;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class DefaultComponentRenderContext implements ComponentRenderContext {
    private final PageRenderContext renderContext;


    public DefaultComponentRenderContext(final PageRenderContext context) {
        this.renderContext = context;
    }

    @Override
    public ComponentRenderContext newInstance(VirtualDomPath path) {
        return new DefaultComponentRenderContext(renderContext.newInstance(path));
    }

    @Override
    public VirtualDomPath rootPath() {
        return renderContext.rootPath();
    }

    @Override
    public LivePage livePage() {
        return renderContext.livePage();
    }

    @Override
    public Map<Event.Target, Event> events() {
        return renderContext.events();
    }

    @Override
    public Map<Ref, VirtualDomPath> refs() {
        return renderContext.refs();
    }

    @Override
    public void setStatusCode(int statusCode) {
        renderContext.setStatusCode(statusCode);
    }

    @Override
    public void setHeaders(Map<String, String> headers) {
        renderContext.setHeaders(headers);
    }

    @Override
    public void setDocType(String docType) {
        renderContext.setDocType(docType);
    }

    @Override
    public void openNode(XmlNs xmlns, String name) {
        renderContext.openNode(xmlns, name);
    }

    @Override
    public void closeNode(String name, boolean upgrade) {
        renderContext.closeNode(name, upgrade);
    }

    @Override
    public void setAttr(XmlNs xmlNs, String name, String value, boolean isProperty) {
        renderContext.setAttr(xmlNs, name, value, isProperty);
    }

    @Override
    public void setStyle(String name, String value) {
        renderContext.setStyle(name, value);
    }

    @Override
    public void addTextNode(String text) {
        renderContext.addTextNode(text);
    }

    @Override
    public void addEvent(Optional<VirtualDomPath> elementPath,
                         String eventName,
                         Consumer<EventContext> eventHandler,
                         boolean preventDefault,
                         Event.Modifier modifier) {

        renderContext.addEvent(elementPath,
                               eventName,
                               eventHandler,
                               preventDefault,
                               modifier);
    }

    @Override
    public void addRef(Ref ref) {
        renderContext.addRef(ref);
    }

    @Override
    public Tag rootTag() {
        return renderContext.rootTag();
    }

    @Override
    public Tag currentTag() {
        return renderContext.currentTag();
    }
}
