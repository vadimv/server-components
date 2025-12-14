package rsp.app.counters;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdate;
import rsp.component.definitions.Component;
import rsp.component.definitions.StoredStateComponent;
import rsp.page.EventContext;

import java.util.Map;
import java.util.function.Consumer;

import static rsp.dsl.Html.*;

public class HideableCounterComponent extends Component<Boolean> {

    private final Map<ComponentCompositeKey, Integer> stateStore;

    public HideableCounterComponent(String name, Map<ComponentCompositeKey, Integer> stateStore) {
        super(HideableCounterComponent.class);
        this.stateStore = stateStore;
    }

    @Override
    public ComponentStateSupplier<Boolean> initStateSupplier() {
        return (_, _) -> true;
    }

    @Override
    public ComponentView<Boolean> componentView() {
        return newState -> state ->
                div(
                        when(state, new CachedCounterComponent("c3", 101, stateStore)),
                        input(attr("type", "checkbox"),
                                when(state, attr("checked", "checked")),
                                attr("id","c3"),
                                attr("name", "c3"),
                                on("click", checkboxClickHandler(state, newState))),
                        label(attr("for", "c3"),
                                text("Show counter 3"))
                );
    }

    private static Consumer<EventContext> checkboxClickHandler(Boolean state, StateUpdate<Boolean> newState) {
        return  _ -> newState.setState(!state);
    }
}
