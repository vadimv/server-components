package rsp.component.definitions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.View;

import java.util.Objects;

/**
 * A component with no state.
 */
public class StatelessComponent extends Component<StatelessComponent.Unit> {

    /**
     * A singleton type representing the absence of a meaningful state.
     */
    public enum Unit {
        /**
         * The single instance of the Unit type.
         */
        INSTANCE
    }

    private final ComponentView<Unit> view;

    public StatelessComponent(final ComponentView<Unit> view) {
        super(StatelessComponent.class);
        this.view = Objects.requireNonNull(view);
    }

    public StatelessComponent(final View<Unit> view) {
        super(StatelessComponent.class);
        Objects.requireNonNull(view);
        this.view =  __ -> view;
    }

    public StatelessComponent(final Object componentType,
                              final ComponentView<Unit> view) {
        super(componentType);
        this.view = Objects.requireNonNull(view);
    }

    @Override
    public ComponentStateSupplier<Unit> initStateSupplier() {
        return (_, _) -> Unit.INSTANCE;
    }

    @Override
    public ComponentView<Unit> componentView() {
        return view;
    }
}
