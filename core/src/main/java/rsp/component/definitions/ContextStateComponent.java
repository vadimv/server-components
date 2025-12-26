package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.component.TreeBuilderFactory;
import rsp.page.events.ComponentEventNotification;
import rsp.page.events.Command;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A component with its state mapped to a value in the parent's context.
 * This class provides a template for creating components whose state is derived from and synchronized with
 * a value provided by an upstream component (e.g., {@link AddressBarSyncComponent}).
 *
 * <p>A subclass must implement {@link #contextValueToStateFunction()} and {@link #stateToContextValueFunction()}
 * to define the mapping between the context's {@link ContextValue} and the component's specific state type {@code S}.
 *
 * @see AddressBarSyncComponent
 * @param <S> this component's state type
 */
public abstract class ContextStateComponent<S> extends Component<S> {
    /**
     * The prefix for component event names used for state synchronization.
     */
    public static final String STATE_UPDATED_EVENT_PREFIX = "stateUpdated.";

    private final String contextAttributeName;

    /**
     * Creates a new instance of a context-bound component.
     * @param contextAttributeName the key used to look up this component's value in the parent context
     */
    public ContextStateComponent(final String contextAttributeName) {
        super(ContextStateComponent.class);
        this.contextAttributeName = Objects.requireNonNull(contextAttributeName);
    }

    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        return (_, componentContext) -> {
            if (componentContext.getAttribute(contextAttributeName) instanceof ContextValue contextValue) {
                return contextValueToStateFunction().apply(contextValue);
            } else {
                throw new IllegalStateException("Attribute " + contextAttributeName + " of type ContextValue not found in component context");
            }
        };
    }

    /**
     * Defines the function that converts a {@link ContextValue} from the parent context into this component's state {@code S}.
     * @return a function for converting the context value to the component's state
     */
    protected abstract Function<ContextValue, S> contextValueToStateFunction();

    /**
     * Defines the function that converts this component's state {@code S} into a {@link ContextValue}
     * to be propagated to the parent component.
     * @return a function for converting the component's state to a context value
     */
    protected abstract Function<S, ContextValue> stateToContextValueFunction();

    @Override
    public ComponentSegment<S> createComponentSegment(final QualifiedSessionId sessionId,
                                                      final TreePositionPath componentPath,
                                                      final TreeBuilderFactory treeBuilderFactory,
                                                      final ComponentContext sessionObjects,
                                                      final Consumer<Command> commandsEnqueue) {
        super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, sessionObjects, commandsEnqueue);
        return new ComponentSegment<>(new ComponentCompositeKey(sessionId, componentType, componentPath),
                                      initStateSupplier(),
                                      subComponentsContext(),
                                      componentView(),
                                      this,
                                      treeBuilderFactory,
                                      sessionObjects,
                                      commandsEnqueue) {


            @Override
            protected boolean onBeforeUpdated(final S state) {
                // notify a component up in the tree hierarchy
                commandsEnqueue.accept(new ComponentEventNotification(STATE_UPDATED_EVENT_PREFIX + contextAttributeName,
                                                                      stateToContextValueFunction().apply(state)));
                return false; // do not update this component, it will be re-rendered as a part of the subtree
            }
        };
    }

    /**
     * A sealed interface representing a value passed through the component context.
     * This provides a type-safe way to handle presence or absence of a value.
     */
    public sealed interface ContextValue {

        /**
         * A singleton representing an empty or absent value.
         */
        ContextValue EMPTY = new Empty();

        /**
         * Represents an empty or absent value in the context.
         */
        record Empty() implements ContextValue {
        }

        /**
         * Represents a string-based value in the context.
         * @param value the string value, must not be null
         */
        record StringValue(String value) implements ContextValue {
            public StringValue {
                Objects.requireNonNull(value);
            }
        }
    }
}
