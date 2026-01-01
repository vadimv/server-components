package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;

public class LayoutComponent extends Component<LayoutComponent.LayoutComponentState> {

    @Override
    public ComponentStateSupplier<LayoutComponentState> initStateSupplier() {
        return (_, _) -> new LayoutComponentState();
    }

    @Override
    public ComponentView<LayoutComponentState> componentView() {
        return null;
    }

    public record LayoutComponentState() {

    }
}
