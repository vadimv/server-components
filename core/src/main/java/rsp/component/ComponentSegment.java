package rsp.component;

import rsp.dom.*;
import rsp.dom.Segment;
import rsp.dsl.Definition;
import rsp.page.EventContext;
import rsp.page.events.GenericTaskEvent;
import rsp.page.events.RemoteCommand;
import rsp.page.events.Command;
import rsp.ref.Ref;

import java.util.*;
import java.util.function.*;

import static java.lang.System.Logger.Level.*;
import static rsp.page.PageBuilder.WINDOW_DOM_PATH;

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
public final class ComponentSegment<S> implements Segment, StateUpdate<S> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final ComponentCompositeKey componentId;
    private final Consumer<Command> commandsEnqueue;
    private final ComponentContext componentContext;
    private final ComponentStateSupplier<S> stateResolver;
    private final BiFunction<ComponentContext, S, ComponentContext> contextResolver;
    private final ComponentView<S> componentView;
    private final ComponentCallbacks<S> callbacks;
    private final TreeBuilderFactory treeBuilderFactory;

    private final List<DomEventEntry> domEventEntries = new ArrayList<>();
    private final List<ComponentEventEntry> componentEventEntries = new ArrayList<>();
    private final Subscriber subscriber = new DefaultSubscriber();
    private final Map<Ref, TreePositionPath> refs = new HashMap<>();
    private final List<ComponentSegment<?>> children = new ArrayList<>();
    private final List<Node> rootNodes = new ArrayList<>();

    private TreePositionPath startNodeDomPath;

    /**
     * This component's current state. It is expected that the state's type is immutable.
     *
     */
    private S state;

    /**
     * Creates a new instance of a component segment.
     *
     * @param componentId an identity of this component
     * @param stateResolver a function to resolve an initial state
     * @param contextResolver a function that builds a context object propagated to descendant components
     * @param componentView contains DOM subtree definition
     * @param callbacks the callbacks invoked during component lifecycle
     * @param treeBuilderFactory a factory for a render context for children components
     * @param componentContext a context object from ascendant components
     * @param commandsEnqueue a consumer for the page's control loop commands
     */
    public ComponentSegment(final ComponentCompositeKey componentId,
                            final ComponentStateSupplier<S> stateResolver,
                            final BiFunction<ComponentContext, S, ComponentContext> contextResolver,
                            final ComponentView<S> componentView,
                            final ComponentCallbacks<S> callbacks,
                            final TreeBuilderFactory treeBuilderFactory,
                            final ComponentContext componentContext,
                            final Consumer<Command> commandsEnqueue) {
        this.componentId = Objects.requireNonNull(componentId);
        this.stateResolver = Objects.requireNonNull(stateResolver);
        this.contextResolver = Objects.requireNonNull(contextResolver);
        this.componentView = Objects.requireNonNull(componentView);
        this.callbacks = Objects.requireNonNull(callbacks);
        this.treeBuilderFactory = Objects.requireNonNull(treeBuilderFactory);
        this.componentContext = Objects.requireNonNull(componentContext);
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue);

        logger.log(TRACE, () -> "New component is created: " + this);
    }

    /**
     * A path representing a position of this component in the components tree
     * @return a position of this component segment in the components tree relative to the tree's root
     */
    public TreePositionPath path() {
        return componentId.componentPath();
    }

    /**
     * This method is invoked by a TreeBuilder during rendering and adds a component to the next position on the included component segments.
     * @param component a ComponentSegment to add
     */
    public void addChild(final ComponentSegment<?> component) {
        children.add(component);
    }

    /**
     *
     * @return
     */
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
        if (rootNodes.isEmpty() || domPath.elementsCount() == this.startNodeDomPath.elementsCount()) {
            rootNodes.add(newNode);
        }
    }

    public void render(final TreeBuilder renderContext) {
        try {
            state = Objects.requireNonNull(stateResolver.getState(componentId, componentContext),
                                           "Initial state cannot be null for component " + componentId);
            renderContext.setComponentContext(contextResolver.apply(componentContext, state));

            final View<S> view = componentView.use(this);
            final Definition uiDefinition = view.apply(state);

            uiDefinition.render(renderContext);
            callbacks.onAfterRendered(state, subscriber, commandsEnqueue, this.new EnqueueTaskStateUpdate());
            callbacks.onMounted(componentId, state, this.new EnqueueTaskStateUpdate());
        } catch (Throwable renderEx) {
            renderContext.addException(renderEx);
            logger.log(DEBUG, () -> "Component " + this + " rendering exception", renderEx);
        }
    }

    /**
     * Sets a new state for this component.
     * The component will be re-rendered.
     * @param newState a new state object, must not be null
     * @throws NullPointerException if the new state is null
     */
    @Override
    public void setState(final S newState) {
        Objects.requireNonNull(newState, "New state cannot be null for component " + componentId);
        applyStateTransformation(_ -> newState);
    }

    @Override
    public void applyStateTransformationIfPresent(final Function<S, Optional<S>> stateTransformer) {
        stateTransformer.apply(state).ifPresent(this::setState);
    }

    /**
     * Updates the current state by applying a state-to-state function.
     * The component will be re-rendered.
     * @param newStateFunction a function for the state transformation, must not return null
     * @throws NullPointerException if the function returns null
     */
    @Override
    public void applyStateTransformation(final UnaryOperator<S> newStateFunction) {
        final S newState = Objects.requireNonNull(newStateFunction.apply(state),
                                                  "State transformer function cannot return null for component " + componentId);

        if (!callbacks.onBeforeUpdated(newState, commandsEnqueue)) {
            return; // update vetoed by component
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

        final TreeBuilder renderContext = treeBuilderFactory.createTreeBuilder(startNodeDomPath);

        renderContext.setComponentContext(contextResolver.apply(componentContext, state));
        renderContext.openComponent(this);
        final Definition view = componentView.use(this).apply(state);
        view.render(renderContext);
        renderContext.closeComponent();
        callbacks.onAfterRendered(state, subscriber, commandsEnqueue, this.new EnqueueTaskStateUpdate());

        // Calculate diff between an old and new DOM trees
        final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
        NodesTreeDiff.diffChildren(oldRootNodes, rootNodes(), startNodeDomPath, domChangePerformer, new HtmlBuilder(new StringBuilder()));
        final Set<TreePositionPath> elementsToRemove = domChangePerformer.elementsToRemove;
        commandsEnqueue.accept(new RemoteCommand.ModifyDom(domChangePerformer.changes));

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

        callbacks.onUpdated(componentId, oldState, state, this.new EnqueueTaskStateUpdate());
    }

    public List<ComponentSegment<?>> directChildren() {
        return children;
    }

    public void unmount() {
        recursiveChildren().forEach(c -> c.unmount());
        callbacks.onUnmounted(componentId, state);
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

    private final class DefaultSubscriber implements Subscriber {

        @Override
        public void addWindowEventHandler(String eventType, Consumer<EventContext> eventHandler, boolean preventDefault, DomEventEntry.Modifier modifier) {
            ComponentSegment.this.addDomEventHandler(WINDOW_DOM_PATH, eventType, eventHandler, preventDefault, modifier);
        }

        @Override
        public void addComponentEventHandler(String eventType, Consumer<ComponentEventEntry.EventContext> eventHandler, boolean preventDefault) {
            ComponentSegment.this.addComponentEventHandler(eventType, eventHandler, preventDefault);
        }
    }

}
