package rsp.compositions.contract;

import rsp.component.definitions.Component;

import java.util.Objects;

/**
 * A component instance that is also its contract runtime.
 */
public final class BoundContractComponent {
    private final Component<?, ?> component;
    private final Contract contract;

    private BoundContractComponent(Component<?, ?> component, Contract contract) {
        this.component = Objects.requireNonNull(component, "component");
        this.contract = Objects.requireNonNull(contract, "contract");
    }

    @SuppressWarnings("rawtypes")
    public static <C extends Component & Contract> BoundContractComponent of(C component) {
        return new BoundContractComponent(component, component);
    }

    public Component<?, ?> component() {
        return component;
    }

    public Contract contract() {
        return contract;
    }
}
