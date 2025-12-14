package rsp.app.counters;

import rsp.component.ComponentView;
import rsp.component.definitions.ContextStateComponent;


public class ContextCounterComponent extends ContextStateComponent<Integer> {

    private final String name;

    public ContextCounterComponent(String name) {
        super(name,
              Integer::parseInt,
              Object::toString);
        this.name = name;
    }

    @Override
    public ComponentView<Integer> componentView() {
        return new CountersView(this.name);
    }

}
