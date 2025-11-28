package rsp.component;

import rsp.dom.*;
import rsp.dom.Segment;
import rsp.html.SegmentDefinition;
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
 * Represents a stateful component which is a part of a components tree.
 * All methods of this class must be called from its page's event loop thread.
 *
 * @param <S> a type for this component's state snapshot, should be an immutable class
 */
public class Component<S> implements Segment, StateUpdate<S> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    protected final ComponentCompositeKey componentId;
    protected final ComponentContext componentContext;
    protected final Consumer<SessionEvent> commandsEnqueue;

    private final ComponentStateSupplier<S> stateResolver;
    private final BiFunction<ComponentContext, S, ComponentContext> contextResolver;
    private final ComponentMountedCallback<S> componentMountedCallback;
    private final ComponentUpdatedCallback<S> componentUpdatedCallback;
    private final ComponentUnmountedCallback<S> componentUnmountedCallback;
    private final ComponentView<S> componentView;
    private final RenderContextFactory renderContextFactory;

    private final List<DomEventEntry> domEventEntries = new ArrayList<>();
    private final List<ComponentEventEntry> componentEventEntries = new ArrayList<>();
    private final Map<Ref, TreePositionPath> refs = new HashMap<>();
    private final List<Component<?>> children = new ArrayList<>();
    private final List<Node> rootNodes = new ArrayList<>();

    private TreePositionPath startNodeDomPath;

    private S state;

    public Component(final ComponentCompositeKey componentId,
                     final ComponentStateSupplier<S> stateResolver,
                     final BiFunction<ComponentContext, S, ComponentContext> contextResolver,
                     final ComponentView<S> componentView,
                     final ComponentCallbacks<S> callbacks,
                     final RenderContextFactory renderContextFactory,
                     final ComponentContext componentContext,
                     final Consumer<SessionEvent> commandsEnqueue) {
        this.componentId = Objects.requireNonNull(componentId);
        this.stateResolver = Objects.requireNonNull(stateResolver);
        this.contextResolver = Objects.requireNonNull(contextResolver);
        this.componentMountedCallback = Objects.requireNonNull(callbacks.componentMountedCallback());
        this.componentView = Objects.requireNonNull(componentView);
        this.componentUpdatedCallback = Objects.requireNonNull(callbacks.componentUpdatedCallback());
        this.componentUnmountedCallback = Objects.requireNonNull(callbacks.componentUnmountedCallback());
        this.renderContextFactory = Objects.requireNonNull(renderContextFactory);
        this.componentContext = Objects.requireNonNull(componentContext);
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

    public void setStartNodeDomPath(final TreePositionPath domPath) {
        if (startNodeDomPath == null) {
            startNodeDomPath = domPath;
        }
    }

    public boolean hasStartNodeDomPath() {
        return startNodeDomPath != null;
    }

    public void addRootDomNode(final TreePositionPath domPath, final Node newNode) {
        if (rootNodes.isEmpty() || domPath.level() == this.startNodeDomPath.level()) {
            rootNodes.add(newNode);
        }
    }

    public void render(final ComponentRenderContext renderContext) {
        try {
            onBeforeInitiallyRendered();
            state = stateResolver.getState(componentId, componentContext);
            renderContext.setComponentContext(contextResolver.apply(componentContext, state));
            final SegmentDefinition view = componentView.apply(this).apply(state);

            view.render(renderContext);
            onAfterRendered(state);
            onAfterMounted(state);
        } catch (Throwable renderEx) {
            logger.log(ERROR, "Component " + this + " rendering exception", renderEx);
        }
    }

    protected S getState() {
        return state;
    }

    @Override
    public void setState(S newState) {
        applyStateTransformation(_ -> newState);
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
    public void applyStateTransformation(UnaryOperator<S> newStateFunction) {
        final S newState = newStateFunction.apply(state);

        if (!onBeforeUpdated(newState)) {
            return;
        }

        final S oldState = state;
        state = newState;

        componentEventEntries.clear();

        refs.clear();

        final List<Node> oldRootNodes = new ArrayList<>(rootNodes());
        rootNodes.clear();

        final Set<DomEventEntry> oldEvents = new HashSet<>(recursiveDomEvents());
        domEventEntries.clear();

        final Set<Component<?>> oldChildren = new HashSet<>(recursiveChildren());
        children.clear();

        logger.log(TRACE, () -> "Component " + this + " old state was " + oldState + " applied new state " + state);

        final ComponentRenderContext renderContext = renderContextFactory.newContext(startNodeDomPath);

        renderContext.setComponentContext(contextResolver.apply(componentContext, state));
        renderContext.openComponent(this);
        final SegmentDefinition view = componentView.apply(this).apply(state);
        view.render(renderContext);
        renderContext.closeComponent();
        onAfterRendered(state);

        // Calculate diff between an old and new DOM trees
        final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
        Diff.diffChildren(oldRootNodes, rootNodes(), startNodeDomPath, domChangePerformer, new HtmlBuilder(new StringBuilder()));
        final Set<TreePositionPath> elementsToRemove = domChangePerformer.elementsToRemove;
        commandsEnqueue.accept(new RemoteCommand.ModifyDom(domChangePerformer.commands));

        // Unregister events
        final List<DomEventEntry> eventsToRemove = new ArrayList<>();
        final Set<DomEventEntry> newEvents = new HashSet<>(recursiveDomEvents());
        for (final DomEventEntry event : oldEvents) {
            if (!newEvents.contains(event)
                && event instanceof DomEventEntry domEventEntry
                && !elementsToRemove.contains(domEventEntry.eventTarget.elementPath())) {
                commandsEnqueue.accept(new RemoteCommand.ForgetEvent(event.eventName, domEventEntry.eventTarget.elementPath()));
            }
        }

        // Register new event types on client
        final List<DomEventEntry> eventsToAdd = new ArrayList<>();
        for (final DomEventEntry event : newEvents) {
            if (!oldEvents.contains(event) && event instanceof DomEventEntry domEventEntry) {
                eventsToAdd.add(domEventEntry);
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
        onAfterUpdated(oldState, state);

    }

    protected void onBeforeInitiallyRendered() {
    }

    protected void onAfterMounted(S state) {
        componentMountedCallback.onComponentMounted(componentId, state, new EnqueueTaskStateUpdate());
    }

    protected void onAfterRendered(S state) {
    }

    protected boolean onBeforeUpdated(S state) {
        return true;
    }

    protected void onAfterUpdated(S oldState, S state) {
        componentUpdatedCallback.onComponentUpdated(componentId, oldState, state, new EnqueueTaskStateUpdate());
    }

    protected void onAfterUnmounted(ComponentCompositeKey key, S oldState) {
        componentUnmountedCallback.onComponentUnmounted(componentId, state);
    }

    public List<Component<?>> directChildren() {
        return children;
    }

    public void unmount() {
        recursiveChildren().forEach(c -> c.unmount());
        onAfterUnmounted(componentId, state);

    }

    private List<Node> rootNodes() {
        if (!rootNodes.isEmpty()) {
            return rootNodes;
        } else {
            final List<Node> nodes = new ArrayList<>();
            for (final Component<?> comp : this.children) {
                nodes.addAll(comp.rootNodes());
            }
            return nodes;
        }
    }

    public List<Component<?>> recursiveChildren() {
        final List<Component<?>> recursiveChildren = new ArrayList<>();
        recursiveChildren.addAll(children);
        for (final Component<?> childComponent : children) {
            recursiveChildren.addAll(childComponent.recursiveChildren());
        }
        return recursiveChildren;
    }

    public List<DomEventEntry> recursiveDomEvents() {
        final List<DomEventEntry> recursiveEvents = new ArrayList<>(domEventEntries);
        for (final Component<?> childComponent : children) {
            recursiveEvents.addAll(childComponent.recursiveDomEvents());
        }
        return recursiveEvents;
    }

    public List<ComponentEventEntry> recursiveComponentEvents() {
        final List<ComponentEventEntry> recursiveEvents = new ArrayList<>(componentEventEntries);
        for (final Component<?> childComponent : children) {
            recursiveEvents.addAll(childComponent.recursiveComponentEvents());
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

    public void addDomEventHandler(final TreePositionPath elementPath,
                                   final String eventType,
                                   final Consumer<EventContext> eventHandler,
                                   final boolean preventDefault,
                                   final DomEventEntry.Modifier modifier) {
        final DomEventEntry.Target eventTarget = new DomEventEntry.Target(elementPath);
        domEventEntries.add(new DomEventEntry(eventType, eventTarget, eventHandler, preventDefault, modifier));
    }

    public void addComponentEventHandler(final String eventType,
                                         final Consumer<ComponentEventEntry.EventContext> eventHandler,
                                         final boolean preventDefault) {
        componentEventEntries.add(new ComponentEventEntry(eventType, eventHandler, preventDefault));
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
