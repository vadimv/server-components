package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;

public class ServicesComponent extends Component<ServicesComponent.ServicesComponentState> {

    @Override
    public ComponentStateSupplier<ServicesComponentState> initStateSupplier() {
        return (_, _) -> new ServicesComponentState();
    }

    @Override
    public ComponentView<ServicesComponentState> componentView() {
        return _ -> _ -> new UiManagementComponent() ;
    }

    public record ServicesComponentState() {
    }
}
