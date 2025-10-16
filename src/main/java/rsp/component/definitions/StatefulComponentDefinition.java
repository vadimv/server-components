package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.TreePositionPath;
import rsp.html.SegmentDefinition;
import rsp.page.PageObjects;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.SessionEvent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * This is the base class for all components definitions.
 * @param <S> this component's state type, should be an immutable class
 */
public abstract class StatefulComponentDefinition<S> implements SegmentDefinition, ComponentFactory<S> {

    protected final Object componentType;

    /**
     * This constructor to be called from the inherited classes.
     * @param componentType a unique object for this component's type
     */
    protected StatefulComponentDefinition(final Object componentType) {
        this.componentType = Objects.requireNonNull(componentType);
    }

    /**
     * This method provides a function for an initial state for this component. The provided value will be used for an initial rendering.
     * The state could be retrieved from a cache.
     * @return a function for an initial state
     */
    protected abstract ComponentStateSupplier<S> stateSupplier();

    /**
     * This method provides this component's view, a tree of segments definitions.
     * The view together with the state will be used for rendering.
     * @return a view for this component
     */
    protected abstract ComponentView<S> componentView();

    /**
     * This method provides a callback for the event when this component is mounted to the segments tree.
     * It is threadsafe to call the state update's methods e.g. to change the component's state in this callback.
     *
     * @return a callback receiving this component's instance key, a current state and a new state update object.
     */
    protected ComponentMountedCallback<S> onComponentMountedCallback() {
        return (key, sessionBag, state, newState) -> {
        };
    }

    /**
     * This method provides a callback for the event when this component's state is updated.
     * It is threadsafe to call the state update's methods e.g. to change the component's state in this callback.
     *
     * @return a callback receiving this component's instance key, a current state and a new state update object.
     */
    protected ComponentUpdatedCallback<S> onComponentUpdatedCallback() {
        return (key,  sessionBag, state, newState) -> {};
    }

    /**
     * This method provides a callback for the event when this component's will be unmounted from the rendered tree.
     * @return a callback receiving a key and a current state.
     */
    protected ComponentUnmountedCallback<S> onComponentUnmountedCallback() {
        return (key, sessionBag, state) -> {};
    }

    @Override
    public Component<S> createComponent(final QualifiedSessionId sessionId,
                                        final TreePositionPath componentPath,
                                        final RenderContextFactory renderContextFactory,
                                        final PageObjects sessionObjects,
                                        final Consumer<SessionEvent> commandsEnqueue) {
        final ComponentCompositeKey key = new ComponentCompositeKey(sessionId, componentType, componentPath);
        return new Component<>(key,
                               stateSupplier(),
                               componentView(),
                               new ComponentCallbacks<>(onComponentMountedCallback(),
                                                        onComponentUpdatedCallback(),
                                                        onComponentUnmountedCallback()),
                               renderContextFactory,
                               sessionObjects,
                               commandsEnqueue);
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        final Component<S> component = renderContext.openComponent(this);
        component.render(renderContext);
        renderContext.closeComponent();
        return true;
    }
}
