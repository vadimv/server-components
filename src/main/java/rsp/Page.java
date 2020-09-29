package rsp;

import rsp.dom.Path;
import rsp.dom.Tag;

import java.util.Map;
import java.util.function.BiFunction;

public class Page<S> {
    public final String path;
    public final Component<S> rootComponent;
    public final S initialState;
    public final BiFunction<String, S, String> state2route;
    public final Tag domRoot;
    public final Map<Event.Target, Event> events;

    public Page(String path,
                Component<S> rootComponent,
                S initialState,
                BiFunction<String, S, String> state2route,
                Tag domRoot,
                Map<Event.Target, Event> events) {
        this.path = path;
        this.rootComponent = rootComponent;
        this.initialState = initialState;
        this.state2route = state2route;
        this.domRoot = domRoot;
        this.events = events;
    }
}
