package rsp.component;

import rsp.component.definitions.StatefulComponent;
import rsp.dom.*;
import rsp.dom.Segment;
import rsp.html.Definition;
import rsp.page.EventContext;
import rsp.page.RenderContextFactory;
import rsp.page.events.GenericTaskEvent;
import rsp.page.events.RemoteCommand;
import rsp.page.events.Command;
import rsp.ref.Ref;

import java.util.*;
import java.util.function.*;

import static java.lang.System.Logger.Level.*;

/**
 * Represents a stateful component which is a part of a UI components tree.
 * Components may contain one or more tags starting HTML DOM subtrees and/or other components.
 * Every component is associated with a state object, managed by the framework.
 * A component's state is set during initialization and can be updated independently as a result of a user's action on this browser's page
 * or events like timers or services callbacks.
 * A change of a component's state results in re-rendering of that component DOM subtree and the subcomponents.
 * A key-value context is propagated to its child components. This context object can be used to pass information from parent components to its children.
 * All methods of this class must be called from its page's event loop thread.
 *
 * @param <S> a type for this component's state snapshot
 */
public class ComponentSegment<S> implements Segment, StateUpdate<S> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    protected final ComponentCompositeKey componentId;
    protected final Consumer<Command> commandsEnqueue;

    private final ComponentContext componentContext;
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
    private final List<ComponentSegment<?>> children = new ArrayList<>();
    private final List<Node> rootNodes = new ArrayList<>();

    private TreePositionPath startNodeDomPath;

    /**
     * This component's current state
     */
    private S state;

    /**
     * Creates a new instance of a component. To be called in a relevant component's definition class.
     * @see StatefulComponent<S>
     *
     * @param componentId an identity of this component, an object to be used as a key to store and retrieve a current state snapshot
     * @param stateResolver a function to resolve an initial state
     * @param contextResolver a function that build a context object that is propagated to descendant components
     * @param componentView contains DOM subtree definition.
     * @param callbacks a bundle of this component's life cycle events callbacks
     * @param renderContextFactory a factory for a render context for children components
     * @param componentContext a context object from ascendant components
     * @param commandsEnqueue a consumer for the page's control loop commands
     */
    public ComponentSegment(final ComponentCompositeKey componentId,
                            final ComponentStateSupplier<S> stateResolver,
                            final BiFunction<ComponentContext, S, ComponentContext> contextResolver,
                            final ComponentView<S> componentView,
                            final ComponentCallbacks<S> callbacks,
                            final RenderContextFactory renderContextFactory,
                            final ComponentContext componentContext,
                            final Consumer<Command> commandsEnqueue) {
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

        logger.log(TRACE, () -> "New component is created: " + this);
    }

    /**
     * A path representing a position of this component in the components tree
     * @return a path in the page's components tree
     */
    public TreePositionPath path() {
        return componentId.componentPath();
    }

    public void addChild(final ComponentSegment<?> component) {
        children.add(component);
    }

    public boolean isRootNodesEmpty() {
        return rootNodes.isEmpty();
    }

    public Node getLastRootNode() {
        return rootNodes.isEmpty() ? null : rootNodes.getLast();
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
            final Definition view = componentView.apply(this).apply(state);

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
    public void setState(final S newState) {
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
    public void applyStateTransformation(final UnaryOperator<S> newStateFunction) {
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

        final Set<ComponentSegment<?>> oldChildren = new HashSet<>(recursiveChildren());
        children.clear();

        logger.log(TRACE, () -> "Component " + this + " old state was " + oldState + " applied new state " + state);

        final ComponentRenderContext renderContext = renderContextFactory.newContext(startNodeDomPath);

        renderContext.setComponentContext(contextResolver.apply(componentContext, state));
        renderContext.openComponent(this);
        final Definition view = componentView.apply(this).apply(state);
        view.render(renderContext);
        renderContext.closeComponent();
        onAfterRendered(state);

        // Calculate diff between an old and new DOM trees
        final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
        Diff.diffChildren(oldRootNodes, rootNodes(), startNodeDomPath, domChangePerformer, new HtmlBuilder(new StringBuilder()));
        final Set<TreePositionPath> elementsToRemove = domChangePerformer.elementsToRemove;
        commandsEnqueue.accept(new RemoteCommand.ModifyDom(domChangePerformer.commands));

        // Unregister events
        final Set<DomEventEntry> newEvents = new HashSet<>(recursiveDomEvents());
        for (final DomEventEntry event : oldEvents) {
            if (!newEvents.contains(event)
                && event instanceof DomEventEntry domEventEntry
                && !elementsToRemove.contains(domEventEntry.eventTarget.elementPath())) {
                commandsEnqueue.accept(new RemoteCommand.ForgetEvent(event.eventName, domEventEntry.eventTarget.elementPath()));
            }
        }

        // Register new events on client-side
        final List<DomEventEntry> eventsToAdd = new ArrayList<>();
        for (final DomEventEntry event : newEvents) {
            if (!oldEvents.contains(event)) {
                eventsToAdd.add(event);
            }
        }
        if (!eventsToAdd.isEmpty()) {
            commandsEnqueue.accept(new RemoteCommand.ListenEvent(eventsToAdd));
        }

        // Notify unmounted child components
        final Set<ComponentSegment<?>> mountedComponents = new HashSet<>(children);
        for (final ComponentSegment<?> child : oldChildren) {
            if (!mountedComponents.contains(child)) {
                child.unmount();
            }
        }

        onAfterUpdated(oldState, state);
    }

    protected void onBeforeInitiallyRendered() {
    }

    protected void onAfterMounted(final S state) {
        componentMountedCallback.onComponentMounted(componentId, state, new EnqueueTaskStateUpdate());
    }

    protected void onAfterRendered(final S state) {
    }

    protected boolean onBeforeUpdated(final S state) {
        return true;
    }

    protected void onAfterUpdated(final S oldState, final S state) {
        componentUpdatedCallback.onComponentUpdated(componentId, oldState, state, new EnqueueTaskStateUpdate());
    }

    protected void onAfterUnmounted(final ComponentCompositeKey key, final S oldState) {
        componentUnmountedCallback.onComponentUnmounted(componentId, state);
    }

    public List<ComponentSegment<?>> directChildren() {
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
            for (final ComponentSegment<?> comp : this.children) {
                nodes.addAll(comp.rootNodes());
            }
            return nodes;
        }
    }

    public List<ComponentSegment<?>> recursiveChildren() {
        final List<ComponentSegment<?>> recursiveChildren = new ArrayList<>();
        recursiveChildren.addAll(children);
        for (final ComponentSegment<?> childComponent : children) {
            recursiveChildren.addAll(childComponent.recursiveChildren());
        }
        return recursiveChildren;
    }

    public List<DomEventEntry> recursiveDomEvents() {
        final List<DomEventEntry> recursiveEvents = new ArrayList<>(domEventEntries);
        for (final ComponentSegment<?> childComponent : children) {
            recursiveEvents.addAll(childComponent.recursiveDomEvents());
        }
        return recursiveEvents;
    }

    public List<ComponentEventEntry> recursiveComponentEvents() {
        final List<ComponentEventEntry> recursiveEvents = new ArrayList<>(componentEventEntries);
        for (final ComponentSegment<?> childComponent : children) {
            recursiveEvents.addAll(childComponent.recursiveComponentEvents());
        }
        return recursiveEvents;
    }


    public Map<Ref, TreePositionPath> recursiveRefs() {
        final Map<Ref, TreePositionPath> recursiveRefs = new HashMap<>(refs);
        for (ComponentSegment<?> childComponent : children) {
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentSegment<?> component = (ComponentSegment<?>) o;
        return componentId.equals(component.componentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId);
    }

    private final class EnqueueTaskStateUpdate implements StateUpdate<S> {
        @Override
        public void setState(final S newState) {
            commandsEnqueue.accept(new GenericTaskEvent(() -> {
                ComponentSegment.this.setState(newState);
            }));
        }

        @Override
        public void setStateWhenComplete(final S newState) {
            commandsEnqueue.accept(new GenericTaskEvent(() -> {
                ComponentSegment.this.setStateWhenComplete(newState);
            }));
        }

        @Override
        public void applyStateTransformation(final UnaryOperator<S> stateTransformer) {
            commandsEnqueue.accept(new GenericTaskEvent(() -> {
                ComponentSegment.this.applyStateTransformation(stateTransformer);
            }));
        }

        @Override
        public void applyStateTransformationIfPresent(final Function<S, Optional<S>> stateTransformer) {
            commandsEnqueue.accept(new GenericTaskEvent(() -> {
                ComponentSegment.this.applyStateTransformationIfPresent(stateTransformer);
            }));
        }
    }
}
