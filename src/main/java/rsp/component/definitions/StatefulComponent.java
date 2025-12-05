package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.TreePositionPath;
import rsp.html.Definition;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.Command;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * This is the base class for all components definitions.
 * @param <S> this component's state type, should be an immutable class
 */
public abstract class StatefulComponent<S> implements Definition, ComponentFactory<S> {

    protected final Object componentType;

    /**
     * This constructor to be called from the inherited classes.
     * @param componentType a unique object for this component's type
     */
    public StatefulComponent(final Object componentType) {
        this.componentType = Objects.requireNonNull(componentType);
    }

    /**
     * This method provides a function for an initial state for this component. The provided value will be used for an initial rendering.
     * The state could be retrieved from a cache.
     * @return a function for an initial state
     */
    public abstract ComponentStateSupplier<S> initStateSupplier();

    /**
     * This method provides this component's view, a tree of segments definitions.
     * The view together with the state will be used for rendering.
     * @return a view for this component
     */
    public abstract ComponentView<S> componentView();


    public BiFunction<ComponentContext, S, ComponentContext> subComponentsContext() {
        return (c, s) -> c;
    }

    /**
     * This method provides a callback for the event when this component is mounted to the segments tree.
     * It is threadsafe to call the state update's methods e.g. to change the component's state in this callback.
     *
     * @return a callback receiving this component's instance key, a current state and a new state update object.
     */
    public ComponentMountedCallback<S> onComponentMountedCallback() {
        return (key,  state, newState) -> {
        };
    }

    /**
     * This method provides a callback for the event when this component's state is updated.
     * It is threadsafe to call the state update's methods e.g. to change the component's state in this callback.
     *
     * @return a callback receiving this component's instance key, a current state and a new state update object.
     */
    public ComponentUpdatedCallback<S> onComponentUpdatedCallback() {
        return (key,  oldState, state, newState) -> {};
    }

    /**
     * This method provides a callback for the event when this component's will be unmounted from the rendered tree.
     * @return a callback receiving a key and a current state.
     */
    public ComponentUnmountedCallback<S> onComponentUnmountedCallback() {
        return (key, state) -> {};
    }

    @Override
    public ComponentSegment<S> createComponent(final QualifiedSessionId sessionId,
                                               final TreePositionPath componentPath,
                                               final RenderContextFactory renderContextFactory,
                                               final ComponentContext componentContext,
                                               final Consumer<Command> commandsEnqueue) {
        return new ComponentSegment<>(new ComponentCompositeKey(sessionId, componentType, componentPath),
                               initStateSupplier(),
                               subComponentsContext(),
                               componentView(),
                               new ComponentCallbacks<>(onComponentMountedCallback(),
                                                        onComponentUpdatedCallback(),
                                                        onComponentUnmountedCallback()),
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
