package rsp.component;

import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.page.EventContext;
import rsp.page.LivePage;
import rsp.page.RenderContext;
import rsp.ref.Ref;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class DefaultComponentRenderContext<S> implements ComponentRenderContext {
    private RenderContext renderContext;
    private final Component<S> component;


    public DefaultComponentRenderContext(final RenderContext context, final Component<S> component) {
        this.renderContext = Objects.requireNonNull(context);
        this.component = Objects.requireNonNull(component);
    }

    public void resetSharedContext(RenderContext sharedContext) {
        component.events.clear();
        renderContext = sharedContext;
    }

    @Override
    public RenderContext sharedContext() {
        return renderContext;
    }

    @Override
    public RenderContext newSharedContext(final VirtualDomPath path) {
        return renderContext.newSharedContext(path);
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
        return Map.copyOf(component.events);
    }

    @Override
    public Map<Ref, VirtualDomPath> refs() {
        return component.refs;
    }

    @Override
    public void setStatusCode(final int statusCode) {
        renderContext.setStatusCode(statusCode);
    }

    @Override
    public void setHeaders(final Map<String, String> headers) {
        renderContext.setHeaders(headers);
    }

    @Override
    public void setDocType(final String docType) {
        renderContext.setDocType(docType);
    }

    @Override
    public void openNode(final XmlNs xmlns, final String name) {
        renderContext.openNode(xmlns, name);
    }

    @Override
    public void closeNode(final String name, final boolean upgrade) {
        renderContext.closeNode(name, upgrade);
    }

    @Override
    public void setAttr(final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
        renderContext.setAttr(xmlNs, name, value, isProperty);
    }

    @Override
    public void setStyle(final String name, final String value) {
        renderContext.setStyle(name, value);
    }

    @Override
    public void addTextNode(final String text) {
        renderContext.addTextNode(text);
    }

    @Override
    public void addEvent(final Optional<VirtualDomPath> elementPath,
                         final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {

        final VirtualDomPath eventPath = elementPath.orElse(renderContext.parentTag().path);
        final Event.Target eventTarget = new Event.Target(eventType, eventPath);
        component.events.put(eventTarget, new Event(eventTarget, eventHandler, preventDefault, modifier));
    }

    @Override
    public void addRef(final Ref ref) {
        component.refs.put(ref, renderContext.parentTag().path);;
    }

    @Override
    public Tag rootTag() {
        return renderContext.rootTag();
    }

    @Override
    public Tag parentTag() {
        return renderContext.parentTag();
    }

    @Override
    public Tag currentTag() {
        return renderContext.currentTag();
    }

    public <S> void addChildComponent(Component<S> childComponent) {
        component.addChildComponent(childComponent);
    }
}
