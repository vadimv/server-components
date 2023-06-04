package rsp.component;

import rsp.CreateViewFunction;
import rsp.dom.*;
import rsp.html.DocumentPartDefinition;
import rsp.html.WindowRef;
import rsp.page.*;
import rsp.page.Timer;
import rsp.ref.Ref;
import rsp.server.In;
import rsp.server.Out;
import rsp.server.Path;
import rsp.util.data.Either;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.System.Logger.Level.DEBUG;


public final class StatefulComponent<S> implements DocumentPartDefinition, In {
    private static final System.Logger logger = System.getLogger(StatefulComponent.class.getName());

    public final CreateViewFunction<S> createViewFunction;

    private S state;
    private Path path;
    private Tag tag;
    private Out out;

    private int descriptorsCounter;
    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();
    //TODO
    public Map<Event.Target, Event> events;
    private Map<Ref, VirtualDomPath> refs;


    public StatefulComponent(final S initialState,
                             final CreateViewFunction<S> createViewFunction) {
        this.state = initialState;
        this.createViewFunction = createViewFunction;
    }


    @Override
    public void render(final PageRenderContext renderContext) {
        if (!(renderContext instanceof ComponentRenderContext)) throw new IllegalArgumentException("ComponentRenderContext is expected");

        final ComponentRenderContext componentRenderContext = (ComponentRenderContext) renderContext;

        final DocumentPartDefinition documentPartDefinition = createViewFunction.apply(state, s -> {
            synchronized (this) {
                state = s;
                out = componentRenderContext.out();
                final Tag oldTag = tag;
                final Set<Event> oldEvents = new HashSet<>(events.values());

                final PageRenderContext prc = componentRenderContext.newInstance();
                if (!(prc instanceof ComponentRenderContext)) throw new IllegalArgumentException("ComponentRenderContext is expected");
                final ComponentRenderContext rc = (ComponentRenderContext) prc;

                render(rc);

                // Calculate diff between currentContext and newContext
                final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
                new Diff(Optional.of(oldTag), rc.tag(), domChangePerformer).run();
                if (domChangePerformer.commands.size() > 0) {
                    out.modifyDom(domChangePerformer.commands);
                }

                // Events
                final Set<Event> newEvents = new HashSet<>(rc.events().values());
                // Unregister events
                final Set<Event> eventsToRemove = new HashSet<>();
                for(final Event event : oldEvents) {
                    if(!newEvents.contains(event) && !domChangePerformer.elementsToRemove.contains(event.eventTarget.elementPath)) {
                        eventsToRemove.add(event);
                    }
                }
                eventsToRemove.forEach(event -> {
                    final Event.Target eventTarget = event.eventTarget;
                    out.forgetEvent(eventTarget.eventType, eventTarget.elementPath);
                });

                // Register new event types on client
                final Set<Event> eventsToAdd = new HashSet<>();
                for(final Event event : newEvents) {
                    if(!oldEvents.contains(event)) {
                        eventsToAdd.add(event);
                    }
                }
                out.listenEvents(new ArrayList<>(eventsToAdd));

                // Browser's navigation
/*                final Path oldPath = path;
            final Path newPath = state2route.stateToPath.apply(state, oldPath);
            if (!newPath.equals(oldPath)) {
                out.pushHistory(state2route.basePath.resolve(newPath).toString());
            }

            path = newPath;*/
            }


        });

        documentPartDefinition.render(renderContext);

        events = componentRenderContext.events();
        tag = componentRenderContext.tag();
        refs = componentRenderContext.refs();

    }

    @Override
    public void handleExtractPropertyResponse(final int descriptorId, final Either<Throwable, JsonDataType> result) {
        result.on(ex -> {
                    logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " exception: " + ex.getMessage());

                    final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
                    if (cf != null) {
                        cf.completeExceptionally(ex);
                        registeredEventHandlers.remove(descriptorId);

                    }
                },
                v -> {
                    logger.log(DEBUG, () -> "extractProperty: " + descriptorId + " value: " + v.toStringValue());
                    synchronized (this) {
                        final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
                        if (cf != null) {
                            cf.complete(v);
                            registeredEventHandlers.remove(descriptorId);
                        }
                    }
                });
    }

    @Override
    public void handleEvalJsResponse(final int descriptorId, final JsonDataType value) {
        logger.log(DEBUG, () -> "evalJsResponse: " + descriptorId + " value: " + value.toStringValue());
        synchronized (this) {
            final CompletableFuture<JsonDataType> cf = registeredEventHandlers.get(descriptorId);
            if (cf != null) {
                cf.complete(value);
                registeredEventHandlers.remove(descriptorId);
            }
        }
    }

    @Override
    public void handleDomEvent(final int renderNumber,
                               final VirtualDomPath path,
                               final String eventType,
                               final JsonDataType.Object eventObject) {
        synchronized (this) {
            VirtualDomPath eventElementPath = path;
            while(eventElementPath.level() > 0) {
                final Event event = events.get(new Event.Target(eventType, eventElementPath));
                if (event != null && event.eventTarget.eventType.equals(eventType)) {
                    final EventContext eventContext = createEventContext(eventObject);
                    event.eventHandler.accept(eventContext);
                    break;
                } else {
                    final Optional<VirtualDomPath> parentPath = eventElementPath.parent();
                    if (parentPath.isPresent()) {
                        eventElementPath = parentPath.get();
                    } else {
                        // TODO warn
                        break;
                    }
                }
            }
        }
    }

    private EventContext createEventContext(final JsonDataType.Object eventObject) {
        return new EventContext(js -> evalJs(js),
                                ref -> createPropertiesHandle(ref),
                                eventObject,
                                new DummySchedule(),
                                href -> setHref(href));
    }

    private void setHref(final String path) {
        out.setHref(path);
    }

    private PropertiesHandle createPropertiesHandle(final Ref ref) {
        final VirtualDomPath path = resolveRef(ref);
        if (path == null) {
            throw new IllegalStateException("Ref not found: " + ref);
        }
        return new PropertiesHandle(path, () -> ++descriptorsCounter, registeredEventHandlers, out);
    }

    private VirtualDomPath resolveRef(final Ref ref) {
        return ref instanceof WindowRef ? VirtualDomPath.DOCUMENT : refs.get(ref);
    }

    public CompletableFuture<JsonDataType> evalJs(final String js) {
        synchronized (this) {
            final int newDescriptor = ++descriptorsCounter;
            final CompletableFuture<JsonDataType> resultHandler = new CompletableFuture<>();
            registeredEventHandlers.put(newDescriptor, resultHandler);
            out.evalJs(newDescriptor, js);
            return resultHandler;
        }
    }

    private static class DummySchedule implements Schedule {
        @Override
        public Timer scheduleAtFixedRate(Runnable command, Object key, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Timer schedule(Runnable command, Object key, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel(Object key) {
            throw new UnsupportedOperationException();
        }
    }

}
