package rsp.app.counters;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentView;
import rsp.component.definitions.StoredStateComponent;

import java.util.Map;

public class CachedCounterComponent extends StoredStateComponent<Integer> {

    private final String name;

    public CachedCounterComponent(String name, int initialState, Map<ComponentCompositeKey, Integer> stateStore) {
        super(initialState, stateStore);
        this.name = name;
    }

    @Override
    public ComponentView<Integer> componentView() {
        return new CountersView(this.name);
    }
}
