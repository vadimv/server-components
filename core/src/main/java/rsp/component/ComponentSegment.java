package rsp.component;

import rsp.dom.*;
import rsp.dom.Segment;
import rsp.dsl.Definition;
import rsp.metrics.MetricNames;
import rsp.metrics.Metrics;
import rsp.page.EventContext;
import rsp.page.events.GenericTaskEvent;
import rsp.page.events.RemoteCommand;
import rsp.ref.Ref;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
 * <h2>Component Reconciliation</h2>
 * When a component re-renders, its direct child component definitions are matched
 * against the previously rendered direct child segments by position and component
 * type. If the previous and candidate segments have the same component type and
 * both runtime policies return {@link ComponentRuntimePolicy#isReusable()}, the
 * previous segment is reused:
 * <ul>
 *     <li>its local state is preserved,</li>
 *     <li>its current upstream {@link ComponentContext} is replaced,</li>
 *     <li>its {@link #contextScope()} notifies watched keys whose values changed,</li>
 *     <li>its view is rendered again, and {@link ComponentCallbacks#onAfterRendered}
 *         is invoked again,</li>
 *     <li>{@link ComponentCallbacks#onMounted} is not invoked again.</li>
 * </ul>
 * If the child cannot be reused, the old segment is unmounted and the candidate
 * segment is mounted normally.
 * <p>
 * Reconciliation is positional. Multiple reusable direct children of the same
 * component type under the same parent are ambiguous when a list, conditional,
 * or tab-like structure can insert, remove, or reorder children. The framework
 * logs a warning for this shape because preserved state can attach to the wrong
 * logical item. Components in such dynamic collections should opt out via
 * {@link ComponentRuntimePolicy#isReusable()} until explicit component keys are
 * available.
 * <h2>Guidance for Component Authors</h2>
 * Reuse is opt-in. A reusable component should treat the upstream context as
 * live, not as a constructor-time snapshot. Prefer creating a
 * {@link ContextLookup} from this segment's {@link #contextScope()} and using
 * {@link ContextLookup#watch} for context values that must refresh while the
 * segment is reused. Components that derive essential state from
 * {@link ComponentStateSupplier} using a one-time context snapshot, keep
 * imperative resources tied to mount, or store a fixed {@link ContextLookup}
 * should keep the default non-reusable policy until they are converted to live
 * context handling.
 *
 * @param <S> a type for this component's state snapshot
 */
public final class ComponentSegment<S> implements Segment, StateUpdate<S> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final ComponentCompositeKey componentId;
    private final CommandsEnqueue commandsEnqueue;
    private final ComponentStateSupplier<S> stateResolver;
    private final BiFunction<ComponentContext, S, ComponentContext> contextResolver;
    private final ComponentView<S> componentView;
    private final ComponentCallbacks<S> callbacks;
    private final ComponentRuntimePolicy runtimePolicy;
    private final TreeBuilderFactory treeBuilderFactory;
    private final Metrics metrics;

    private final List<DomEventEntry> domEventEntries = new ArrayList<>();
    private final List<ComponentEventEntry> componentEventEntries = new ArrayList<>(); // should it be a dictionary?
    private final Map<ComponentEventEntry, ComponentSegment<?>> componentEventOwners =
            new IdentityHashMap<>();
    private final Subscriber subscriber = new DefaultSubscriber();
    private final Map<Ref, TreePositionPath> refs = new HashMap<>();
    private final List<ComponentSegment<?>> children = new ArrayList<>();
    private final List<Node> rootNodes = new ArrayList<>();
    private final Set<ContextScope.Controller> contextMirrors = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * The context propagated from ancestors. Mutable: the framework's reconciliation path
     * may update this via {@link #setComponentContext(ComponentContext)} when a segment is
     * reused across a parent re-render. User code must not modify this directly.
     */
    private final ContextScope contextScope;
    private TreePositionPath startNodeDomPath;
    private TagNode parentTag;
    private boolean isUnmounted;
    private boolean stateInitialized;
    private List<ComponentSegment<?>> previousChildrenForReconciliation = List.of();
    private Set<ComponentSegment<?>> claimedChildrenForReconciliation = Set.of();

    /**
     * This component's current state. It is expected that the state's type is immutable.
     *
     */
    private S state;

    private static final ThreadLocal<ComponentSegment<?>> CALLBACK_OWNER = new ThreadLocal<>();
    private static final Set<String> AMBIGUOUS_RECONCILIATION_WARNINGS = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new instance of a component segment.
     *
     * @param componentId an identity of this component
     * @param stateResolver a function to resolve an initial state
     * @param contextResolver a function that builds a context object propagated to descendant components
     * @param componentView contains DOM subtree definition
     * @param callbacks the callbacks invoked during component lifecycle
     * @param runtimePolicy declarative runtime decisions for this component
     * @param treeBuilderFactory a factory for a render context for children components
     * @param componentContext a context object from ascendant components
     * @param commandsEnqueue a consumer for the page's control loop commands
     */
    public ComponentSegment(final ComponentCompositeKey componentId,
                            final ComponentStateSupplier<S> stateResolver,
                            final BiFunction<ComponentContext, S, ComponentContext> contextResolver,
                            final ComponentView<S> componentView,
                            final ComponentCallbacks<S> callbacks,
                            final ComponentRuntimePolicy runtimePolicy,
                            final TreeBuilderFactory treeBuilderFactory,
                            final ComponentContext componentContext,
                            final CommandsEnqueue commandsEnqueue) {
        this.componentId = Objects.requireNonNull(componentId);
        this.stateResolver = Objects.requireNonNull(stateResolver);
        this.contextResolver = Objects.requireNonNull(contextResolver);
        this.componentView = Objects.requireNonNull(componentView);
        this.callbacks = Objects.requireNonNull(callbacks);
        this.runtimePolicy = Objects.requireNonNull(runtimePolicy);
        this.treeBuilderFactory = Objects.requireNonNull(treeBuilderFactory);
        this.contextScope = new ContextScope(Objects.requireNonNull(componentContext));
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue);
        this.metrics = Metrics.from(componentContext);

        this.metrics.incrementCounter(MetricNames.SEGMENT_CREATED);
        logger.log(TRACE, () -> "New component is created: " + this);
    }

    /**
     * Creates a segment with the default runtime policy.
     * <p>
     * This constructor is primarily used by low-level tests and custom segment
     * factories that do not need to customize reconciliation or subscriber
     * boundary behavior.
     */
    public ComponentSegment(final ComponentCompositeKey componentId,
                            final ComponentStateSupplier<S> stateResolver,
                            final BiFunction<ComponentContext, S, ComponentContext> contextResolver,
                            final ComponentView<S> componentView,
                            final ComponentCallbacks<S> callbacks,
                            final TreeBuilderFactory treeBuilderFactory,
                            final ComponentContext componentContext,
                            final CommandsEnqueue commandsEnqueue) {
        this(componentId,
             stateResolver,
             contextResolver,
             componentView,
             callbacks,
             ComponentRuntimePolicy.DEFAULT,
             treeBuilderFactory,
             componentContext,
             commandsEnqueue);
    }

    /**
     * A path representing a position of this component in the components tree
     * @return a position of this component segment in the components tree relative to the tree's root
     */
    public TreePositionPath path() {
        return componentId.componentPath();
    }

    /**
     * @return the context currently associated with this segment.
     * <p>
     * Used as the source for lazy lookups that follow the segment's current context
     * (including any update from {@link #setComponentContext(ComponentContext)}):
     * pass {@link #contextScope()} to {@link ContextLookup} when a component also
     * needs to observe future context changes.
     * <p>
     * Read-only. The setter is package-private and reserved for the framework's
     * reconciliation path.
     */
    public ComponentContext componentContext() {
        return contextScope.current();
    }

    /**
     * @return this segment's live context scope.
     * <p>
     * Used by framework-created {@link ContextLookup} instances so components can
     * observe context changes when a segment is reused. The scope can be watched by
     * user code, but its current context can only be replaced inside this package.
     */
    public ContextScope contextScope() {
        return contextScope;
    }

    /**
     * Mirrors this segment's current context into an external scope owned by a
     * higher-level runtime object.
     * <p>
     * The mirror is updated immediately and on future {@link #setComponentContext}
     * calls. Unmounting this segment drops the mirror reference but does not clear
     * the external scope; the owner is responsible for its lifecycle.
     */
    public void mirrorContextTo(final ContextScope.Controller controller) {
        Objects.requireNonNull(controller, "controller");
        if (contextMirrors.add(controller)) {
            controller.replace(componentContext());
        }
    }

    /**
     * Update the context associated with this segment.
     * <p>
     * Package-private. Called only by the framework's reconciliation path when a segment
     * is reused across a parent re-render and its parent's enriched context has changed.
     * User code must not call this directly.
     */
    void setComponentContext(final ComponentContext componentContext) {
        final ComponentContext next = Objects.requireNonNull(componentContext, "componentContext");
        this.contextScope.replace(next);
        replaceContextMirrors(next);
    }

    private void replaceContextMirrors(final ComponentContext componentContext) {
        for (final ContextScope.Controller mirror : contextMirrors) {
            mirror.replace(componentContext);
        }
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

    void setParentTag(final TagNode parentTag) {
        if (this.parentTag == null) {
            this.parentTag = parentTag;
        }
    }

    public void addRootDomNode(final TreePositionPath domPath, final Node newNode) {
        if (rootNodes.isEmpty() || domPath.elementsCount() == this.startNodeDomPath.elementsCount()) {
            rootNodes.add(newNode);
        }
    }

    public void render(final TreeBuilder renderContext) {
        if (stateInitialized) {
            renderReused(renderContext);
            return;
        }
        try {
            state = Objects.requireNonNull(stateResolver.getState(componentId, componentContext()),
                                           "Initial state cannot be null for component " + componentId);
            stateInitialized = true;
            renderContext.setComponentContext(descendantContextResolver().apply(componentContext(), state));

            final View<S> view = componentView.use(this);
            final Definition uiDefinition = view.apply(state);

            uiDefinition.render(renderContext);
            withCallbackOwner(this, () ->
                    callbacks.onAfterRendered(state, subscriber, commandsEnqueue, this.new EnqueueTaskStateUpdate()));
            withCallbackOwner(this, () ->
                    callbacks.onMounted(componentId, state, this.new EnqueueTaskStateUpdate()));
        } catch (Throwable renderEx) {
            renderContext.addException(renderEx);
            logger.log(DEBUG, () -> "Component " + this + " rendering exception", renderEx);
        }
    }

    private void renderReused(final TreeBuilder renderContext) {
        final List<ComponentSegment<?>> oldChildren = new ArrayList<>(children);
        prepareForRender(true, oldChildren, true);
        try {
            renderContext.setComponentContext(descendantContextResolver().apply(componentContext(), state));
            final View<S> view = componentView.use(this);
            final Definition uiDefinition = view.apply(state);
            uiDefinition.render(renderContext);
            withCallbackOwner(this, () ->
                    callbacks.onAfterRendered(state, subscriber, commandsEnqueue, this.new EnqueueTaskStateUpdate()));
        } catch (Throwable renderEx) {
            renderContext.addException(renderEx);
            logger.log(DEBUG, () -> "Component " + this + " rendering exception", renderEx);
        } finally {
            finishChildReconciliation();
        }
    }

    private BiFunction<ComponentContext, S, ComponentContext> descendantContextResolver() {
        return (ctx, s) -> {
            final ComponentContext resolved = contextResolver.apply(ctx, s);
            return runtimePolicy.providesSubscriberBoundary()
                    ? resolved.with(Subscriber.class, subscriber)
                    : resolved;
        };
    }

    private void prepareForRender(final boolean refreshOwnComponentHandlers,
                                  final List<ComponentSegment<?>> oldChildren,
                                  final boolean resetParentTag) {
        if (refreshOwnComponentHandlers) {
            removeComponentEventHandlersOwnedBy(this);
        }
        if (resetParentTag) {
            parentTag = null;
        }
        refs.clear();
        rootNodes.clear();
        domEventEntries.clear();
        children.clear();
        beginChildReconciliation(oldChildren);
    }

    private void beginChildReconciliation(final List<ComponentSegment<?>> oldChildren) {
        previousChildrenForReconciliation = oldChildren;
        claimedChildrenForReconciliation = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private void finishChildReconciliation() {
        try {
            for (final ComponentSegment<?> oldChild : previousChildrenForReconciliation) {
                if (!claimedChildrenForReconciliation.contains(oldChild)) {
                    oldChild.unmount();
                    removeComponentEventHandlersOwnedBy(oldChild);
                }
            }
            warnIfAmbiguousReconciliation();
        } finally {
            previousChildrenForReconciliation = List.of();
            claimedChildrenForReconciliation = Set.of();
        }
    }

    @SuppressWarnings("unchecked")
    <T> ComponentSegment<T> reconcileChild(final ComponentSegment<T> candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (previousChildrenForReconciliation.isEmpty()) {
            return null;
        }

        final int childIndex = children.size();
        if (childIndex >= previousChildrenForReconciliation.size()) {
            return null;
        }

        final ComponentSegment<?> previous = previousChildrenForReconciliation.get(childIndex);
        claimedChildrenForReconciliation.add(previous);

        if (previous.canReuse(candidate)) {
            final ComponentSegment<T> reusable = (ComponentSegment<T>) previous;
            reusable.rebindFrom(candidate);
            candidate.discardUnrendered();
            return reusable;
        }

        previous.unmount();
        removeComponentEventHandlersOwnedBy(previous);
        return null;
    }

    private boolean canReuse(final ComponentSegment<?> candidate) {
        return !isUnmounted
                && Objects.equals(componentId.componentType(), candidate.componentId.componentType())
                && runtimePolicy.isReusable()
                && candidate.runtimePolicy.isReusable();
    }

    private void rebindFrom(final ComponentSegment<S> candidate) {
        setComponentContext(candidate.componentContext());
    }

    private void discardUnrendered() {
        isUnmounted = true;
        contextScope.clear();
        contextMirrors.clear();
    }

    private void warnIfAmbiguousReconciliation() {
        final Map<Object, Integer> reusableCountsByType = new HashMap<>();
        for (final ComponentSegment<?> child : children) {
            if (child.runtimePolicy.isReusable()) {
                reusableCountsByType.merge(child.componentId.componentType(), 1, Integer::sum);
            }
        }

        for (final var entry : reusableCountsByType.entrySet()) {
            if (entry.getValue() <= 1) {
                continue;
            }
            final String warningKey = componentId.componentType() + "|" + entry.getKey();
            if (AMBIGUOUS_RECONCILIATION_WARNINGS.add(warningKey)) {
                logger.log(WARNING, () ->
                        "Ambiguous component reconciliation under " + componentId
                                + ": " + entry.getValue()
                                + " reusable unkeyed children of type " + entry.getKey()
                                + ". Reuse is positional; state may attach to the wrong item after "
                                + "insert/remove/reorder. Use explicit keys when available or override "
                                + "isReusable() to false.");
            }
        }
    }

    private static void withCallbackOwner(final ComponentSegment<?> owner, final Runnable callback) {
        final ComponentSegment<?> previous = CALLBACK_OWNER.get();
        CALLBACK_OWNER.set(owner);
        try {
            callback.run();
        } finally {
            if (previous == null) {
                CALLBACK_OWNER.remove();
            } else {
                CALLBACK_OWNER.set(previous);
            }
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
        if (isUnmounted) {
            logger.log(WARNING, () -> "Ignored state update on unmounted component: " + componentId);
            metrics.incrementCounter(MetricNames.SEGMENT_UPDATE_DROPPED_UNMOUNTED);
            return;
        }
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
        if (isUnmounted) {
            logger.log(WARNING, () -> "Ignored state update on unmounted component: " + componentId);
            metrics.incrementCounter(MetricNames.SEGMENT_UPDATE_DROPPED_UNMOUNTED);
            return;
        }

        final S newState = Objects.requireNonNull(newStateFunction.apply(state),
                                                  "State transformer function cannot return null for component " + componentId);

        if (!callbacks.onBeforeUpdated(newState, commandsEnqueue)) {
            return; // update vetoed by component
        }

        final S oldState = state;
        state = newState;

        // Snapshot everything BEFORE starting tearing down or clearing.
        final List<Node> oldRootNodes = new ArrayList<>(rootNodes());
        final Set<DomEventEntry> oldEvents = new HashSet<>(recursiveDomEvents());
        final List<ComponentSegment<?>> oldChildren = new ArrayList<>(directChildren());

        prepareForRender(true, oldChildren, false);

        logger.log(TRACE, () -> "Component state updated, previous: " + oldState + " new: " + state + " for " + componentId);

        final TreeBuilder renderContext = treeBuilderFactory.createTreeBuilder(startNodeDomPath);

        try {
            renderContext.setComponentContext(descendantContextResolver().apply(componentContext(), state));
            renderContext.openComponent(this);
            final Definition view = componentView.use(this).apply(state);
            view.render(renderContext);
            renderContext.closeComponent();
            withCallbackOwner(this, () ->
                    callbacks.onAfterRendered(state, subscriber, commandsEnqueue, this.new EnqueueTaskStateUpdate()));
        } finally {
            finishChildReconciliation();
        }

        // Calculate diff between an old and new DOM trees
        final DefaultDomChangesContext domChangePerformer = new DefaultDomChangesContext();
        NodesTreeDiff.diffChildren(oldRootNodes, rootNodes(), startNodeDomPath, domChangePerformer, new HtmlBuilder(new StringBuilder()));
        final Set<TreePositionPath> elementsToRemove = domChangePerformer.elementsToRemove;
        commandsEnqueue.offer(new RemoteCommand.ModifyDom(domChangePerformer.changes));

        // Keep parent component's tag tree in sync with this component's latest root nodes
        updateParentTagTree(oldRootNodes);

        // Unregister events
        final Set<DomEventEntry> newEvents = new HashSet<>(recursiveDomEvents());
        for (final DomEventEntry event : oldEvents) {
            if (!newEvents.contains(event)
                && event instanceof DomEventEntry domEventEntry
                && !elementsToRemove.contains(domEventEntry.eventTarget.elementPath())) {
                commandsEnqueue.offer(new RemoteCommand.ForgetEvent(event.eventName, domEventEntry.eventTarget.elementPath()));
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
            commandsEnqueue.offer(new RemoteCommand.ListenEvent(eventsToAdd));
        }

        withCallbackOwner(this, () ->
                callbacks.onUpdated(componentId, oldState, state, this.new EnqueueTaskStateUpdate()));
    }

    public List<ComponentSegment<?>> directChildren() {
        return children;
    }

    public void unmount() {
        if (isUnmounted) {
            return;
        }
        isUnmounted = true;
        recursiveChildren().forEach(c -> c.unmount());
        withCallbackOwner(this, () -> callbacks.onUnmounted(componentId, state));
        componentEventOwners.clear();
        componentEventEntries.clear();
        contextScope.clear();
        contextMirrors.clear();
        metrics.incrementCounter(MetricNames.SEGMENT_UNMOUNTED);
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

    /**
     * After this component re-renders, replace its old root nodes in the parent tag's children list
     * with the new ones. This keeps the parent component's tag tree in sync with the client DOM,
     * so that when the parent re-renders, the diff is computed against the actual current state.
     */
    private void updateParentTagTree(final List<Node> oldRootNodes) {
        if (parentTag == null || oldRootNodes.isEmpty()) {
            return;
        }
        final List<Node> newRootNodes = rootNodes();
        // Find first old root node in parent's children by identity
        int startIdx = -1;
        for (int i = 0; i < parentTag.children.size(); i++) {
            if (parentTag.children.get(i) == oldRootNodes.get(0)) {
                startIdx = i;
                break;
            }
        }
        if (startIdx >= 0) {
            for (int i = 0; i < oldRootNodes.size(); i++) {
                parentTag.children.remove(startIdx);
            }
            for (int i = 0; i < newRootNodes.size(); i++) {
                parentTag.children.add(startIdx + i, newRootNodes.get(i));
            }
        } else {
            throw new IllegalStateException("Component " + componentId + " is not referenced by its parent component's tags");
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

    public Lookup.Registration addComponentEventHandler(final String eventType,
                                                        final Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                        final boolean preventDefault) {
        final ComponentEventEntry entry = new ComponentEventEntry(eventType, eventHandler, preventDefault);
        componentEventEntries.add(entry);
        final ComponentSegment<?> owner = CALLBACK_OWNER.get();
        if (owner != null) {
            componentEventOwners.put(entry, owner);
        }
        return () -> removeComponentEventHandler(entry);
    }

    private void removeComponentEventHandler(final ComponentEventEntry entry) {
        componentEventEntries.removeIf(e -> e == entry);
        componentEventOwners.remove(entry);
    }

    private void removeComponentEventHandlersOwnedBy(final ComponentSegment<?> owner) {
        if (componentEventOwners.isEmpty()) {
            return;
        }
        final Iterator<ComponentEventEntry> iterator = componentEventEntries.iterator();
        while (iterator.hasNext()) {
            final ComponentEventEntry entry = iterator.next();
            if (componentEventOwners.get(entry) == owner) {
                iterator.remove();
                componentEventOwners.remove(entry);
            }
        }
    }

    /**
     * Register a type-safe handler for a component event.
     *
     * @param <T> the payload type
     * @param key the typed event key
     * @param handler receives the event name and typed payload
     * @param preventDefault (currently unused for component events)
     * @return a {@link Lookup.Registration} that removes this specific handler when invoked
     */
    public <T> Lookup.Registration addEventHandler(final EventKey<T> key,
                                                   final java.util.function.BiConsumer<String, T> handler,
                                                   final boolean preventDefault) {
        return addComponentEventHandler(key.name(), ctx -> {
            @SuppressWarnings("unchecked")
            T payload = (T) ctx.eventObject();
            handler.accept(ctx.eventName(), payload);
        }, preventDefault);
    }

    /**
     * Register a type-safe handler for a void event (no payload).
     *
     * @param key the void event key
     * @param handler the handler to invoke
     * @param preventDefault (currently unused for component events)
     * @return a {@link Lookup.Registration} that removes this specific handler when invoked
     */
    public Lookup.Registration addEventHandler(final EventKey.VoidKey key,
                                               final Runnable handler,
                                               final boolean preventDefault) {
        return addComponentEventHandler(key.name(), ctx -> handler.run(), preventDefault);
    }

    /**
     * Register a type-safe handler for a component event (convenience overload).
     *
     * @param <T> the payload type
     * @param key the typed event key
     * @param handler receives the event name and typed payload
     * @return a {@link Lookup.Registration} that removes this specific handler when invoked
     */
    public <T> Lookup.Registration addEventHandler(final EventKey<T> key,
                                                   final java.util.function.BiConsumer<String, T> handler) {
        return addEventHandler(key, handler, false);
    }

    /**
     * Register a type-safe handler for a void event (convenience overload).
     *
     * @param key the void event key
     * @param handler the handler to invoke
     * @return a {@link Lookup.Registration} that removes this specific handler when invoked
     */
    public Lookup.Registration addEventHandler(final EventKey.VoidKey key,
                                               final Runnable handler) {
        return addEventHandler(key, handler, false);
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
            commandsEnqueue.offer(new GenericTaskEvent(() -> {
                ComponentSegment.this.setState(newState);
            }));
        }

        @Override
        public void applyStateTransformation(final UnaryOperator<S> stateTransformer) {
            commandsEnqueue.offer(new GenericTaskEvent(() -> {
                ComponentSegment.this.applyStateTransformation(stateTransformer);
            }));
        }

        @Override
        public void applyStateTransformationIfPresent(final Function<S, Optional<S>> stateTransformer) {
            commandsEnqueue.offer(new GenericTaskEvent(() -> {
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
        public Lookup.Registration addComponentEventHandler(String eventType, Consumer<ComponentEventEntry.EventContext> eventHandler, boolean preventDefault) {
            return ComponentSegment.this.addComponentEventHandler(eventType, eventHandler, preventDefault);
        }
    }

}
