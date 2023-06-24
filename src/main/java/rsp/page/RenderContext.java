package rsp.page;

import rsp.component.Component;
import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.ref.Ref;
import rsp.server.HttpRequest;
import rsp.stateview.ComponentView;
import rsp.stateview.NewState;
import rsp.util.data.Tuple2;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
    <S> Tuple2<S, NewState<S>> openComponent(Function<HttpRequest, CompletableFuture<S>> initialStateFuncion, ComponentView<S> view);
    <S> void openComponent(Component<S> component);
    void closeComponent();
    Tag rootTag();
    <S> Component<S> rootComponent();
    Tag parentTag();
    Tag currentTag();
    RenderContext newSharedContext(VirtualDomPath path);
    VirtualDomPath rootPath();
    LivePage livePage();
}
