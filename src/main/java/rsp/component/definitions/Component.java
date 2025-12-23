package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.TreePositionPath;
import rsp.dsl.Definition;
import rsp.page.QualifiedSessionId;
import rsp.component.TreeBuilderFactory;
import rsp.page.events.Command;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The base class for components definitions.
 * On a page tree, a component represent a self-contained unit of a UI with its own subtree consisting of a mix of other components and/or DOM elements.
 * A definitions' tree is rendered to a DOM tree when states are resolved.
 * <p>
 * Subclasses of this class provide an implementation an initial state supplier and a function defining of its UI subtree.
 * Optionally they provide a way to pass information to the component's downstream component which these components can use to set up states.
 * There are a number of methods available for overriding representing callbacks for various phases of the defined component's lifecycle.
 * The mechanisms of extension described above can be described of the application level.
 * <p>
 * Also, it is possible to override the method which creates component's segment which can be overridden as well to substitute the new instance with
 * the one of extended ComponentSegment class. This gives more control over the created segment's behaviour.
 * @see ComponentSegment<S>
 * @param <S> this component's state type
 */
public abstract class Component<S> implements Definition, ComponentSegmentFactory<S>, ComponentSegmentLifeCycle<S> {

    protected final Object componentType;

    /**
     * This constructor to be called from the inherited classes.
     * @param componentType a unique object for this component's type
     */
    public Component(final Object componentType) {
        this.componentType = Objects.requireNonNull(componentType);
    }

    /**
     * The default constructor sets the component's type to its class.
     */
    public Component() {
        this.componentType = this.getClass();
    }

    /**
     * This method's implementation provides a function for an initial state for this component. The result value will be used for an initial rendering.
     * For example, the state could be provided in a constructor or retrieved from a cache.
     * @return a function for an initial state
     */
    public abstract ComponentStateSupplier<S> initStateSupplier();

    /**
     * This method provides this component's view, which is a composition of segments definitions.
     * The view together with the state will be used for rendering.
     * @return a view for this component
     */
    public abstract ComponentView<S> componentView();

    /**
     * Provides a capability to define common context for downstream components states
     * @return a function for creating components context to be uses in the wrapped components subtree
     */
    public BiFunction<ComponentContext, S, ComponentContext> subComponentsContext() {
        return (c, s) -> c;
    }

    /**
     * This method provides a callback for the event when this component is mounted to the segments tree.
     * It is threadsafe to call the state update's methods e.g. to change the component's state in this callback.

     */
    public void onComponentMounted(ComponentCompositeKey componentId, S state, StateUpdate<S> stateUpdate) {
        Objects.requireNonNull(componentId);
        Objects.requireNonNull(state);
        Objects.requireNonNull(stateUpdate);
    }

    /**
     * This method provides a callback for the event when this component's state is updated.
     * It is threadsafe to call the state update's methods e.g. to change the component's state in this callback.
     *
     */
    public void onComponentUpdated(ComponentCompositeKey componentId, S oldState, S newState, StateUpdate<S> stateUpdate) {
        Objects.requireNonNull(componentId);
        Objects.requireNonNull(oldState);
        Objects.requireNonNull(newState);
        Objects.requireNonNull(stateUpdate);
    }

    /**
     * This method provides a callback for the event when this component's will be unmounted from the rendered tree.
     */
    public void onComponentUnmounted(ComponentCompositeKey componentId, S state) {
        Objects.requireNonNull(componentId);
        Objects.requireNonNull(state);
    }

    @Override
    public ComponentSegment<S> createComponentSegment(final QualifiedSessionId sessionId,
                                                      final TreePositionPath componentPath,
                                                      final TreeBuilderFactory treeBuilderFactory,
                                                      final ComponentContext componentContext,
                                                      final Consumer<Command> commandsEnqueue) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(componentPath);
        Objects.requireNonNull(treeBuilderFactory);
        Objects.requireNonNull(componentContext);
        Objects.requireNonNull(commandsEnqueue);
        return new ComponentSegment<>(new ComponentCompositeKey(sessionId, componentType, componentPath),
                                      initStateSupplier(),
                                      subComponentsContext(),
                                      componentView(),
                                      this,
                                      treeBuilderFactory,
                                      componentContext,
                                      commandsEnqueue);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        Objects.requireNonNull(renderContext);
        final ComponentSegment<S> component = renderContext.openComponent(this);
        component.render(renderContext);
        renderContext.closeComponent();
    }
}
