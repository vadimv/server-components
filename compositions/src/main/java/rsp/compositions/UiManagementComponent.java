package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;

public class UiManagementComponent extends Component<UiManagementComponent.UiManagementComponentState> {
    @Override
    public ComponentStateSupplier<UiManagementComponentState> initStateSupplier() {
        return (_, _) -> new UiManagementComponentState();
    }

    @Override
    public ComponentView<UiManagementComponentState> componentView() {
        return _ -> _ -> new LayoutComponent();
    }

    public record UiManagementComponentState() {
    }
}
