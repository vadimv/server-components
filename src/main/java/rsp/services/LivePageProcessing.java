package rsp.services;

import rsp.*;
import rsp.dom.*;
import rsp.server.InMessage;
import rsp.server.InMessages;
import rsp.server.OutMessages;
import rsp.server.SerializeKorolevOutMessages;
import rsp.state.UseState;

import javax.websocket.Session;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LivePageProcessing implements InMessages {

    private final AtomicInteger descriptorsCounter = new AtomicInteger();

    public LivePageProcessing() {

    }

    @Override
    public void extractProperty(int descriptorId, String value) {

    }

    @Override
    public void domEvent(int renderNumber, Path path, String eventType) {

    }
/*
    public static <S> LivePageProcessing of(Map<QualifiedSessionId, Page<S>> pagesStorage, OutMessages out) {
        final QualifiedSessionId qsid = new QualifiedSessionId(session.getPathParameters().get("pid"), session.getPathParameters().get("sid"));
        final Page<S> page = pagesStorage.get(qsid);
        if(page == null) {
            // a connection after server's restart
            out.evalJs(0, "location.reload()");
            return;
        } else {
            pagesStorage.remove(qsid);
            session.getUserProperties().put("current-dom", page.domRoot);
            session.getUserProperties().put("root-component", page.rootComponent);
            session.getUserProperties().put("path", page.path);
        };
        final UseState<S> useState = new UseState<S>() {
            private volatile S state = page.initialState;
            @Override
            public void accept(S newState) {
                final String newRoute = page.state2route.apply(page.path, newState);
                if(newRoute.equals(page.path)) {
                    state = newState;

                    final DomTreeRenderContext<S> newContext = new DomTreeRenderContext<>();
                    final EnrichingXhtmlContext<S> newEnrichingContext = new EnrichingXhtmlContext<>(newContext,
                            qsid.sessionId,
                            "/",
                            DefaultConnectionLostWidget.HTML,
                            5000);
                    final Component<S> root = (Component<S>) session.getUserProperties().get("root-component");
                    root.materialize(this).accept(newEnrichingContext);

                    // calculate diff between currentContext and newContext
                    final var currentRoot = (Tag) session.getUserProperties().get("current-dom");
                    final var remoteChangePerformer = new RemoteDomChangesPerformer();
                    new Diff(currentRoot, newContext.root, remoteChangePerformer).run();
                    remoteChangePerformer.commands.forEach(command -> {
                        out.modifyDom(command);
                    });
                    session.getUserProperties().put("current-dom", newContext.root);

                } else {
                    // send new URL/route command
                    System.out.println("New route: " + newRoute);
                }
            }

            @Override
            public S get() {
                return state;
            }
        };
        final DomTreeRenderContext<S> domTreeRenderContext = new DomTreeRenderContext<>();
        final Map<String, CompletableFuture<?>> registeredEventHandlers = new ConcurrentHashMap<>();
        final EnrichingXhtmlContext<S> enrichingDomTreeRenderContext = new EnrichingXhtmlContext<>(domTreeRenderContext,
                qsid.sessionId,
                "/",
                DefaultConnectionLostWidget.HTML,
                5000);
        page.rootComponent.materialize(useState).accept(enrichingDomTreeRenderContext);
        session.getUserProperties().put("current-dom", domTreeRenderContext.root);
        out.setRenderNum(0);//TODO

        // Register event types on client
        domTreeRenderContext.events.entrySet().stream().map(e -> e.getValue().eventType).distinct().forEach(eventType -> {
            out.listenEvent(eventType, false);
        });
        return new LivePageProcessing();
    }

    @Override
    public void extractProperty(int descriptorId, String value) {

    }

    @Override
    public void domEvent(int renderNumber, Path path, String eventType) {
        Path eventElementPath = path;
        while(eventElementPath.level() > 0) {
            Event event = domTreeRenderContext.events.get(eventElementPath);
            if(event != null && event.eventType.equals(domEvent.eventType)) {
                final EventContext eventContext = new EventContext(() -> descriptorsCounter.incrementAndGet(),
                        registeredEventHandlers,
                        ref -> domTreeRenderContext.refs.get(ref),
                        command -> sendText(session, command));
                event.eventHandler.accept(eventContext);
                break;
            } else {
                eventElementPath = eventElementPath.parent().get();
            }
        }
    } **/
}
