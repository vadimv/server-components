package rsp.component;

import rsp.dom.*;
import rsp.dom.Segment;
import rsp.html.SegmentDefinition;
import rsp.page.PageObjects;
import rsp.page.EventContext;
import rsp.page.RenderContextFactory;
import rsp.page.events.GenericTaskEvent;
import rsp.page.events.RemoteCommand;
import rsp.page.events.SessionEvent;
import rsp.ref.Ref;

import java.util.*;
import java.util.function.*;

import static java.lang.System.Logger.Level.*;

/**
 * Represents a stateful component.
 * @param <S> a type for this component's state snapshot, should be an immutable class
 */
public class Component<S> implements Segment, StateUpdate<S> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    protected final ComponentCompositeKey componentId;
    protected final PageObjects sessionObjects;
    protected final Consumer<SessionEvent> commandsEnqueue;

    private final ComponentStateSupplier<S> stateResolver;
    private final ComponentMountedCallback<S> componentMountedCallback;
    private final ComponentUpdatedCallback<S> componentUpdatedCallback;
    private final ComponentUnmountedCallback<S> componentUnmountedCallback;
    private final ComponentView<S> componentView;
    private final RenderContextFactory renderContextFactory;

    private final List<Event> events = new ArrayList<>();
    private final Map<Ref, TreePositionPath> refs = new HashMap<>();
    private final List<Component<?>> children = new ArrayList<>();
    private final List<Node> rootNodes = new ArrayList<>();
    private TreePositionPath startNodeDomPath;

    private S state;



    public Component(final ComponentCompositeKey componentId,
                     final ComponentStateSupplier<S> stateResolver,
                     final ComponentView<S> componentView,
                     final ComponentCallbacks<S> componentCallbacks,
                     final RenderContextFactory renderContextFactory,
                     final PageObjects sessionObjects,
                     final Consumer<SessionEvent> commandsEnqueue) {
        this.componentId = Objects.requireNonNull(componentId);
        this.stateResolver = Objects.requireNonNull(stateResolver);
        this.componentMountedCallback = Objects.requireNonNull(componentCallbacks.componentMountedCallback());
        this.componentView = Objects.requireNonNull(componentView);
        this.componentUpdatedCallback = Objects.requireNonNull(componentCallbacks.componentUpdatedCallback());
        this.componentUnmountedCallback = Objects.requireNonNull(componentCallbacks.componentUnmountedCallback());
        this.renderContextFactory = Objects.requireNonNull(renderContextFactory);
        this.sessionObjects = Objects.requireNonNull(sessionObjects);
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue);

        logger.log(TRACE, () -> "New component is created with key " + this);
    }

    public TreePositionPath path() {
        return componentId.componentPath();
    }

    public void addChild(final Component<?> component) {
        children.add(component);
    }

    public boolean isRootNodesEmpty() {
        return rootNodes.isEmpty();
    }

    public Node getLastRootNode() {
        return rootNodes.get(rootNodes.size() - 1);
    }

    public void notifyNodeOpened(final TreePositionPath domPath, final Node newNode) {
        if (startNodeDomPath == null) {
            startNodeDomPath = domPath;
        }

        if (rootNodes.isEmpty() || domPath.level() == this.startNodeDomPath.level()) {
            rootNodes.add(newNode);
        }
    }

    public void render(final ComponentRenderContext renderContext) {
        try {
            onBeforeComponentMount();
            state = stateResolver.getState(componentId, (key) -> sessionObjects.ofComponent(componentId).get(key));
            final SegmentDefinition view = componentView.apply(this).apply(state);
            view.render(renderContext);

            onComponentMounted(state);

        } catch (Throwable renderEx) {
            logger.log(ERROR, "Component " + this + " rendering exception", renderEx);
        }
    }

    @Override
    public void setState(final S newState) {
        applyStateTransformation(s -> newState);
    }

    @Override
    public void setStateWhenComplete(final S newState) {
        setState(newState);
    }

    @Override
    public void applyStateTransformationIfPresent(final Function<S, Optional<S>> stateTransformer) {
        stateTransformer.apply(state).ifPresent(this::setState);
    }

    @Override
    public void applyStateTransformation(final UnaryOperator<S> newStateFunction) {
        final List<Node> oldRootNodes = new ArrayList<>(rootNodes);
        rootNodes.clear();
        final Set<Event> oldEvents = new HashSet<>(recursiveEvents());
        final Set<Component<?>> oldChildren = new HashSet<>(recursiveChildren());
        final S oldState = state;
        state = newStateFunction.apply(state);

        logger.log(TRACE, () -> "Component " + this + " old state was " + oldState + " applied new state " + state);

        final ComponentRenderContext renderContext = renderContextFactory.newContext(startNodeDomPath);

        events.clear();
        refs.clear();
        children.clear();

        renderContext.openComponent(this);
        final SegmentDefinition view = componentView.apply(this).apply(state);
        view.render(renderContext);
        renderContext.closeComponent();

        onUpdateRendered(state);

        // Calculate diff between an old and new DOM trees
        final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
        Diff.diffChildren(oldRootNodes, rootNodes, startNodeDomPath, domChangePerformer, new HtmlBuilder(new StringBuilder()));
        final Set<TreePositionPath> elementsToRemove = domChangePerformer.elementsToRemove;
        commandsEnqueue.accept(new RemoteCommand.ModifyDom(domChangePerformer.commands));

        // Unregister events
        final List<Event> eventsToRemove = new ArrayList<>();
        final Set<Event> newEvents = new HashSet<>(recursiveEvents());
        for (Event event : oldEvents) {
            if (!newEvents.contains(event) && !elementsToRemove.contains(event.eventTarget.elementPath())) {
                eventsToRemove.add(event);
            }
        }
        for (Event event : eventsToRemove) {
            final Event.Target eventTarget = event.eventTarget;
            commandsEnqueue.accept(new RemoteCommand.ForgetEvent(eventTarget.eventType(), eventTarget.elementPath()));
        }

        // Register new event types on client
        final List<Event> eventsToAdd = new ArrayList<>();
        for (final Event event : newEvents) {
            if (!oldEvents.contains(event)) {
                eventsToAdd.add(event);
            }
        }
        if (!eventsToAdd.isEmpty()) {
            commandsEnqueue.accept(new RemoteCommand.ListenEvent(eventsToAdd));
        }

        // Notify unmounted child components
        final Set<Component<?>> mountedComponents = new HashSet<>(children);
        for (final Component<?> child : oldChildren) {
            if (!mountedComponents.contains(child)) {
                child.unmount();
            }
        }
        onComponentUpdated(oldState, state);

    }

    protected void onBeforeComponentMount() {
        //componentBeforeMountCallback.onBeforeComponentMount(componentId, sessionObjects.ofComponent(componentId));
    }

    protected void onComponentMounted(S state) {
        componentMountedCallback.onComponentMounted(componentId, sessionObjects.ofComponent(componentId), state, new EnqueueTaskStateUpdate());
    }

    protected void onComponentUpdated(S oldState, S state) {
        componentUpdatedCallback.onComponentUpdated(componentId, sessionObjects.ofComponent(componentId), oldState, state, new EnqueueTaskStateUpdate());
    }

    protected void onUpdateRendered(S state) {}

    protected void onUnmounted(ComponentCompositeKey key, S oldState) {}

    public List<Component<?>> directChildren() {
        return children;
    }

    public void unmount() {
        recursiveChildren().forEach(c -> c.unmount());
        onUnmounted(componentId, state);
        componentUnmountedCallback.onComponentUnmounted(componentId, sessionObjects.ofComponent(componentId), state);
    }

    public List<Component<?>> recursiveChildren() {
        final List<Component<?>> recursiveChildren = new ArrayList<>();
        recursiveChildren.addAll(children);
        for (final Component<?> childComponent : children) {
            recursiveChildren.addAll(childComponent.recursiveChildren());
        }
        return recursiveChildren;
    }

    public List<Event> recursiveEvents() {
        final List<Event> recursiveEvents = new ArrayList<>();
        recursiveEvents.addAll(events);
        for (final Component<?> childComponent : children) {
            recursiveEvents.addAll(childComponent.recursiveEvents());
        }
        return recursiveEvents;
    }

    public Map<Ref, TreePositionPath> recursiveRefs() {
        final Map<Ref, TreePositionPath> recursiveRefs = new HashMap<>(refs);
        for (Component<?> childComponent : children) {
            recursiveRefs.putAll(childComponent.recursiveRefs());
        }
        return recursiveRefs;
    }

    public void addEvent(final TreePositionPath elementPath,
                         final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
        final Event.Target eventTarget = new Event.Target(eventType, elementPath);
        events.add(new Event(eventTarget, eventHandler, preventDefault, modifier));
    }

    public void addRef(final Ref ref, final TreePositionPath path) {
        refs.put(ref, path);
    }

    public void html(final HtmlBuilder hb) {
        // TODO fix
        if (rootNodes.isEmpty()) {
            children.forEach( component -> component.html(hb));
        } else {
            rootNodes.forEach(node -> hb.buildHtml(node));
        }
    }

    @Override
    public String toString() {
        return "Component{" +
                "key=" + componentId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Component<?> component = (Component<?>) o;
        return componentId.equals(component.componentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId);
    }

    private final class EnqueueTaskStateUpdate implements StateUpdate<S> {
        @Override
        public void setState(S newState) {
            commandsEnqueue.accept(new GenericTaskEvent(() -> {
                Component.this.setState(newState);
            }));
        }

        @Override
        public void setStateWhenComplete(S newState) {
            commandsEnqueue.accept(new GenericTaskEvent(() -> {
                Component.this.setStateWhenComplete(newState);
            }));
        }

        @Override
        public void applyStateTransformation(UnaryOperator<S> stateTransformer) {
            commandsEnqueue.accept(new GenericTaskEvent(() -> {
                Component.this.applyStateTransformation(stateTransformer);
            }));
        }

        @Override
        public void applyStateTransformationIfPresent(Function<S, Optional<S>> stateTransformer) {
            commandsEnqueue.accept(new GenericTaskEvent(() -> {
                Component.this.applyStateTransformationIfPresent(stateTransformer);
            }));
        }
    }
}
