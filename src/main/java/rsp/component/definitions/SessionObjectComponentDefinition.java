package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.Event;
import rsp.dom.TreePositionPath;
import rsp.page.PageObjects;
import rsp.page.PageRendering;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.DomEvent;
import rsp.page.events.SessionEvent;
import rsp.util.json.JsonDataType;

import java.util.function.Consumer;
import java.util.function.Function;

public class SessionObjectComponentDefinition<S> extends StatefulComponentDefinition<S> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final String name;
    private final Function<String, S> keyToStateFunction;
    private final Function<S, String> stateToKeyFunction;
    private final ComponentView<S> view;

    public SessionObjectComponentDefinition(String name,
                                            Function<String, S> keyToStateFunction,
                                            Function<S, String> stateToKeyFunction,
                                            final ComponentView<S> view) {
        super(SessionObjectComponentDefinition.class);
        this.name = name;
        this.keyToStateFunction = keyToStateFunction;
        this.stateToKeyFunction = stateToKeyFunction;
        this.view = view;
    }

    @Override
    protected ComponentStateSupplier<S> stateSupplier() {
        return (key, sessionLookup) -> {
            final String value = (String) sessionLookup.apply(name);
            logger.log(System.Logger.Level.TRACE, () -> "");
            return keyToStateFunction.apply(value);
        };
    }

    @Override
    protected ComponentView<S> componentView() {
        return view;
    }


    @Override
    public Component<S> createComponent(QualifiedSessionId sessionId,
                                        TreePositionPath componentPath,
                                        RenderContextFactory renderContextFactory,
                                        PageObjects sessionObjects,
                                        Consumer<SessionEvent> commandsEnqueue) {
        final ComponentCompositeKey componentId = new ComponentCompositeKey(sessionId, componentType, componentPath);// TODO
                        return new Component<>(componentId,
                                                stateSupplier(),
                                                componentView(),
                                                new ComponentCallbacks<>(onComponentMountedCallback(),
                                                        onComponentUpdatedCallback(),
                                                        onComponentUnmountedCallback()),
                                                renderContextFactory,
                                                sessionObjects,
                                                commandsEnqueue) {

                            @Override
                            protected void onComponentMounted(S state) {
                                //subscribe for history events
                                this.addEventHandler(PageRendering.WINDOW_DOM_PATH,
                                        "historyUndo." + name,
                                        eventContext -> {
                                            final String value = sessionObjects.get("value").toString();
                                            this.setState(keyToStateFunction.apply(value))  ;
                                        },
                                        true,
                                        Event.NO_MODIFIER);
                            }

                            @Override
                            protected void onComponentUpdated(S oldState, S state, Object obj) {
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
