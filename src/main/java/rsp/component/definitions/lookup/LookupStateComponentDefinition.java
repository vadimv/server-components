package rsp.component.definitions.lookup;

import rsp.component.*;
import rsp.component.definitions.StatefulComponentDefinition;
import rsp.dom.Event;
import rsp.dom.TreePositionPath;
import rsp.page.Lookup;
import rsp.page.PageRendering;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.DomEvent;
import rsp.page.events.SessionEvent;
import rsp.util.json.JsonDataType;

import java.util.function.Consumer;
import java.util.function.Function;

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
    public ComponentStateSupplier<S> stateSupplier() {
        return (key, sessionLookup) -> {
            final String value = (String) sessionLookup.apply(name);
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
                                        Lookup sessionObjects,
                                        Consumer<SessionEvent> commandsEnqueue) {
        return new Component<>(new ComponentCompositeKey(sessionId, componentType, componentPath),
                               stateSupplier(),
                               componentView(),
                               new ComponentCallbacks<>(onComponentMountedCallback(),
                                                        onComponentUpdatedCallback(),
                                                        onComponentUnmountedCallback()),
                               renderContextFactory,
                               sessionObjects,
                               commandsEnqueue) {

            @Override
            protected void onAfterInitiallyRendered(S state) {
                //subscribe for history events
                this.addEventHandler(PageRendering.WINDOW_DOM_PATH,
                        "historyUndo." + name,
                        eventContext -> {
                            final String value = lookup.get("value").toString();
                            this.setState(keyToStateFunction.apply(value))  ;
                        },
                        true,
                        Event.NO_MODIFIER);
            }

            @Override
            protected void onAfterUpdated(S oldState, S state, Object obj) {
                //subscribe for history events
                this.addEventHandler(PageRendering.WINDOW_DOM_PATH,
                        "historyUndo." + name,
                        eventContext -> {
                            final String value = eventContext.eventObject().value("value").get().asJsonString().value();
                            this.setState(keyToStateFunction.apply(value), "history")  ;
                        },
                        true,
                        Event.NO_MODIFIER);

                if (obj == null) { // is not from an history undo
                    commandsEnqueue.accept(new DomEvent(1,
                                           PageRendering.WINDOW_DOM_PATH, "stateUpdated." + name,
                                           new JsonDataType.Object().put("value", new JsonDataType.String(stateToKeyFunction.apply(state)))));
                }

            }
        };
    }
}
