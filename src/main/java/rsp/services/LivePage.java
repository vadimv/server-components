package rsp.services;

import rsp.*;
import rsp.dom.*;
import rsp.server.InMessages;
import rsp.server.OutMessages;
import rsp.state.MutableState;
import rsp.state.UseState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class LivePage<S> implements InMessages {
    private final AtomicInteger descriptorsCounter = new AtomicInteger();
    private final Map<Integer, CompletableFuture<String>> registeredEventHandlers = new ConcurrentHashMap<>();

    private final UseState<S> useState;
    private final UseState<Tag> currentDom;
    private final UseState<Map<Path, Event>> currentEvents;
    private final UseState<Map<Ref, Path>> currentRefs;
    private final OutMessages out;

    public LivePage(UseState<S> useState,
                    UseState<Tag> currentDom,
                    UseState<Map<Path, Event>> currentEvents,
                    UseState<Map<Ref, Path>> currentRefs,
                    OutMessages out) {
        this.useState = useState;
        this.currentDom = currentDom;
        this.currentEvents = currentEvents;
        this.currentRefs = currentRefs;
        this.out = out;
    }

    public static <S> Optional<LivePage<S>> of(Map<QualifiedSessionId, Page<S>> pagesStorage,
                                               Map<String, String> pathParameters,
                                               BiFunction<QualifiedSessionId, RenderContext<S>, RenderContext<S>> contextEnrich,
                                               OutMessages out) {
        final QualifiedSessionId qsid = new QualifiedSessionId(pathParameters.get("pid"), pathParameters.get("sid"));
        final Page<S> page = pagesStorage.get(qsid);
        if(page == null) {
            // a connection after server's restart
            out.evalJs(0, "location.reload()");
            return Optional.empty();
        }

        pagesStorage.remove(qsid);

        final UseState<Tag> currentDomRoot = new MutableState<>(page.domRoot);
        final UseState<Component<S>> currentRootComponent = new MutableState<>(page.rootComponent);
        final UseState<Map<Path, Event>> currentEvents = new MutableState<>(new HashMap<>());
        final UseState<S> useState = new UseState<S>() {
            private volatile S state = page.initialState;
            @Override
            public void accept(S newState) {
                final String newRoute = page.state2route.apply(page.path, newState);
                if(newRoute.equals(page.path)) {
                    state = newState;

                    final DomTreeRenderContext<S> newContext = new DomTreeRenderContext<>();
                    final Component<S> root = currentRootComponent.get();
                    root.materialize(this).accept(contextEnrich.apply(qsid, newContext));

                    // calculate diff between currentContext and newContext
                    final var currentRoot = currentDomRoot.get();
                    final var remoteChangePerformer = new RemoteDomChangesPerformer();
                    try {
                        new Diff(currentRoot, newContext.root, remoteChangePerformer).run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    out.modifyDom(remoteChangePerformer.commands);
                    currentDomRoot.accept(newContext.root);
                    currentEvents.accept(newContext.events);
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
        page.rootComponent.materialize(useState).accept(contextEnrich.apply(qsid, domTreeRenderContext));
        currentDomRoot.accept(domTreeRenderContext.root);
        currentEvents.accept(domTreeRenderContext.events);
        final UseState<Map<Ref, Path>> currentRefs = new MutableState<>(domTreeRenderContext.refs);
        out.setRenderNum(0);//TODO

        // Register event types on client
        domTreeRenderContext.events.entrySet().stream().map(e -> e.getValue().eventType).distinct().forEach(eventType -> {
            if(!eventType.equals("submit")) // TODO check why a form submit event should not be registered
                out.listenEvent(eventType, false);
        });

        return Optional.of(new LivePage<>(useState,
                                          currentDomRoot,
                                          currentEvents,
                                          new MutableState<>(domTreeRenderContext.refs),
                                          out));
    }

    @Override
    public void extractProperty(int descriptorId, String value) {
        System.out.println("extractProperty:" + descriptorId + " value=" + value);
        final var cf = registeredEventHandlers.get(descriptorId);
        if(cf != null) {
            cf.complete(value);
        }
    }

    @Override
    public void domEvent(int renderNumber, Path path, String eventType) {
        Path eventElementPath = path;
        while(eventElementPath.level() > 1) {
            Event event = currentEvents.get().get(eventElementPath);
            if(event != null && event.eventType.equals(eventType)) {
                final EventContext eventContext = new EventContext(() -> descriptorsCounter.incrementAndGet(),
                                                                    registeredEventHandlers,
                                                                    ref -> currentRefs.get().get(ref),
                                                                    out);
                event.eventHandler.accept(eventContext);
                break;
            } else {
                eventElementPath = eventElementPath.parent().get();
            }
        }
    }
}
