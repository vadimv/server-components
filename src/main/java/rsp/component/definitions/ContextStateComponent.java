package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.component.TreeBuilderFactory;
import rsp.page.events.ComponentEventNotification;
import rsp.page.events.Command;
import rsp.util.json.JsonDataType;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A component with its state mapped to a key-value in the components' context.
 * @see AddressBarSyncComponent as an example of a component up in the tree which syncs context with the browser's page address bar elements
 *
 * @param <S> this component's state type
 */
public class ContextStateComponent<S> extends Component<S> {
    /**
     * The prefix for mapped attributes names
     */
    public static final String STATE_UPDATED_EVENT_PREFIX = "stateUpdated.";

    /**
     * The field name for a state's change notification event object value
     */
    public static final String STATE_VALUE_ATTRIBUTE_NAME = "value";

    private final System.Logger logger = System.getLogger(getClass().getName());

    private final String contextAttributeName;
    private final Function<String, S> contextValueToStateFunction;
    private final Function<S, String> stateToContextValueFunction;
    private final ComponentView<S> view;

    /**
     *
     * @param contextAttributeName
     * @param contextValueToStateFunction
     * @param stateToContextValueFunction
     * @param view
     */
    public ContextStateComponent(final String contextAttributeName,
                                 final Function<String, S> contextValueToStateFunction,
                                 final Function<S, String> stateToContextValueFunction,
                                 final ComponentView<S> view) {
        super(ContextStateComponent.class);
        this.contextAttributeName = Objects.requireNonNull(contextAttributeName);
        this.contextValueToStateFunction = Objects.requireNonNull(contextValueToStateFunction);
        this.stateToContextValueFunction = Objects.requireNonNull(stateToContextValueFunction);
        this.view = Objects.requireNonNull(view);
    }

    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        return (_, componentContext) -> {
            final String value = (String) componentContext.getAttribute(contextAttributeName);
            return contextValueToStateFunction.apply(value);
        };
    }

    @Override
    public ComponentView<S> componentView() {
        return view;
    }

    @Override
    public ComponentSegment<S> createComponentSegment(final QualifiedSessionId sessionId,
                                                      final TreePositionPath componentPath,
                                                      final TreeBuilderFactory treeBuilderFactory,
                                                      final ComponentContext sessionObjects,
                                                      final Consumer<Command> commandsEnqueue) {
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
                                       new JsonDataType.Object().put(STATE_VALUE_ATTRIBUTE_NAME,
                                                                     new JsonDataType.String(stateToContextValueFunction.apply(state)))));
                return false; // do not update this component, it will be re-rendered as a part of the subtree
            }
        };
    }
}
