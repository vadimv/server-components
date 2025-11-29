package rsp.component.definitions.lookup;

import rsp.component.*;
import rsp.component.definitions.StatefulComponentDefinition;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.ComponentEventNotification;
import rsp.page.events.Command;
import rsp.util.json.JsonDataType;

import java.util.function.Consumer;
import java.util.function.Function;

import static rsp.component.definitions.lookup.AddressBarLookupComponentDefinition.*;

public class LookupStateComponentDefinition<S> extends StatefulComponentDefinition<S> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final String name;
    private final Function<String, S> keyToStateFunction;
    private final Function<S, String> stateToKeyFunction;
    private final ComponentView<S> view;

    public LookupStateComponentDefinition(String name,
                                          Function<String, S> keyToStateFunction,
                                          Function<S, String> stateToKeyFunction,
                                          final ComponentView<S> view) {
        super(LookupStateComponentDefinition.class);
        this.name = name;
        this.keyToStateFunction = keyToStateFunction;
        this.stateToKeyFunction = stateToKeyFunction;
        this.view = view;
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
    public Component<S> createComponent(QualifiedSessionId sessionId,
                                        TreePositionPath componentPath,
                                        RenderContextFactory renderContextFactory,
                                        ComponentContext sessionObjects,
                                        Consumer<Command> commandsEnqueue) {
        return new Component<>(new ComponentCompositeKey(sessionId, componentType, componentPath),
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
                return false; // do not render this component
            }

        };
    }
}
