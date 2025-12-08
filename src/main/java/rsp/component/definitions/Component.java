package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.TreePositionPath;
import rsp.dsl.Definition;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.Command;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * This is the base class for components definitions.
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
     * Provides a capability to define components context for downstream components states
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
    }

    /**
     * This method provides a callback for the event when this component's state is updated.
     * It is threadsafe to call the state update's methods e.g. to change the component's state in this callback.
     *
     */
    public void onComponentUpdated(ComponentCompositeKey componentId, S oldState, S newState, StateUpdate<S> stateUpdate) {
    }

    /**
     * This method provides a callback for the event when this component's will be unmounted from the rendered tree.
     */
    public void onComponentUnmounted(ComponentCompositeKey componentId, S state) {
    }

    @Override
    public ComponentSegment<S> createComponentSegment(final QualifiedSessionId sessionId,
                                                      final TreePositionPath componentPath,
                                                      final RenderContextFactory renderContextFactory,
                                                      final ComponentContext componentContext,
                                                      final Consumer<Command> commandsEnqueue) {
        return new ComponentSegment<>(new ComponentCompositeKey(sessionId, componentType, componentPath),
                                      initStateSupplier(),
                                      subComponentsContext(),
                                      componentView(),
                                      this,
                                      renderContextFactory,
                                      componentContext,
                                      commandsEnqueue);
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        final ComponentSegment<S> component = renderContext.openComponent(this);
        component.render(renderContext);
        renderContext.closeComponent();
        return true;
    }
}
