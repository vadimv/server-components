package rsp.compositions;

import rsp.component.definitions.Component;

@FunctionalInterface
public interface UiComponent<T extends ViewContract> {
    Component<Object> apply(T contract);
}
