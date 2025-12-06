package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.ComponentEventNotification;
import rsp.page.events.Command;
import rsp.util.json.JsonDataType;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class ContextComponent<S> extends StatefulComponent<S> {
    public static final String STATE_UPDATED_EVENT_PREFIX = "stateUpdated.";
    public static final String STATE_VALUE_ATTRIBUTE_NAME = "value";

    private final System.Logger logger = System.getLogger(getClass().getName());

    private final String name;
    private final Function<String, S> keyToStateFunction;
    private final Function<S, String> stateToKeyFunction;
    private final ComponentView<S> view;

    public ContextComponent(final String name,
                            final Function<String, S> keyToStateFunction,
                            final Function<S, String> stateToKeyFunction,
                            final ComponentView<S> view) {
        super(ContextComponent.class);
        this.name = Objects.requireNonNull(name);
        this.keyToStateFunction = Objects.requireNonNull(keyToStateFunction);
        this.stateToKeyFunction = Objects.requireNonNull(stateToKeyFunction);
        this.view = Objects.requireNonNull(view);
    }

    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        return (_, componentContext) -> {
            final String value = (String) componentContext.getAttribute(name);
            return keyToStateFunction.apply(value);
        };
    }

    @Override
    public ComponentView<S> componentView() {
        return view;
    }


    @Override
    public ComponentSegment<S> createComponent(QualifiedSessionId sessionId,
                                               TreePositionPath componentPath,
                                               RenderContextFactory renderContextFactory,
                                               ComponentContext sessionObjects,
                                               Consumer<Command> commandsEnqueue) {
        return new ComponentSegment<>(new ComponentCompositeKey(sessionId, componentType, componentPath),
                               initStateSupplier(),
                               subComponentsContext(),
                               componentView(),
                               new ComponentCallbacks<>(onComponentMountedCallback(),
                                                        onComponentUpdatedCallback(),
                                                        onComponentUnmountedCallback()),
                               renderContextFactory,
                               sessionObjects,
                               commandsEnqueue) {


            protected boolean onBeforeUpdated(S state) {
                commandsEnqueue.accept(new ComponentEventNotification(STATE_UPDATED_EVENT_PREFIX + name,
                                       new JsonDataType.Object().put(STATE_VALUE_ATTRIBUTE_NAME,
                                                                     new JsonDataType.String(stateToKeyFunction.apply(state)))));
                return false; // do not update this component
            }

        };
    }
}
