package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.AddressBarSyncComponent;
import rsp.component.definitions.Component;
import rsp.server.http.RelativeUrl;

import java.util.List;

public class RoutingComponent extends Component<RoutingComponent.RoutingComponentState> {

    @Override
    public ComponentStateSupplier<RoutingComponentState> initStateSupplier() {
        return (_, _) -> new RoutingComponentState();
    }

    @Override
    public ComponentView<RoutingComponentState> componentView() {
        return _ -> _ -> new AddressBarSyncComponent(null) {
            @Override
            public List<PositionKey> pathElementsPositionKeys() {
                return List.of();
            }

            @Override
            public List<ParameterNameKey> queryParametersNamedKeys() {
                return List.of();
            }

            @Override
            public ComponentView<RelativeUrl> componentView() {
                return _ ->_ -> new ServicesComponent();
            }
        };
    }

    public record RoutingComponentState() {
    }
}
