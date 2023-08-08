package rsp.page;

import rsp.component.Component;
import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.ref.Ref;
import rsp.server.Path;
import rsp.component.ComponentView;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public interface RenderContext {
    void setStatusCode(int statusCode);
    void setHeaders(Map<String, String> headers);
    void setDocType(String docType);
    void openNode(XmlNs xmlns, String name);
    void closeNode(String name, boolean upgrade);
    void setAttr(XmlNs xmlNs, String name, String value, boolean isProperty);
    void setStyle(String name, String value);
    void addTextNode(String text);
    void addEvent(Optional<VirtualDomPath> elementPath,
                  String eventName,
                  Consumer<EventContext> eventHandler,
                  boolean preventDefault,
                  Event.Modifier modifier);
    void addRef(Ref ref);
    <T, S> Component<T, S> openComponent(Class<T> stateReferenceClass,
                                         Function<T, CompletableFuture<? extends S>> initialStateFunction,
                                         BiFunction<S, Path, Path> state2pathFunction,
                                         ComponentView<S> componentView);
    <T, S> void openComponent(Component<T, S> component);
    void closeComponent();
    Tag rootTag();
    <T, S> Component<T, S> rootComponent();
    RenderContext newContext(VirtualDomPath path);
    RenderContext newContext();
}
