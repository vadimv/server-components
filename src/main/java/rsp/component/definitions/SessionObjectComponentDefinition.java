package rsp.component.definitions;

import rsp.component.ComponentMountedCallback;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentUpdatedCallback;
import rsp.component.ComponentView;

import java.util.function.Function;

public class SessionObjectComponentDefinition<S> extends StatefulComponentDefinition<S> {

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
        return (key, session) -> {
            final String value = (String) session.apply(name);
            return keyToStateFunction.apply(value);
        };
    }

    @Override
    protected ComponentView<S> componentView() {
        return view;
    }

    @Override
    protected ComponentMountedCallback<S> onComponentMountedCallback() {
        return (key, sessionBag, state, newState) -> {
            sessionBag.onValueUpdated(name, obj -> {
                System.out.println("Update value:" + obj);
                final String value = (String) obj;
                newState.setState(keyToStateFunction.apply(value));
            });
        };
    }

    @Override
    protected ComponentUpdatedCallback<S> onComponentUpdatedCallback() {
        return (key, sessionBag, state, newState) -> {
            sessionBag.put(name, stateToKeyFunction.apply(state));
        };
    }
}
