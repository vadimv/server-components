package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.server.http.HttpRequest;

import java.util.List;

public class AppComponent extends Component<AppComponent.AppComponentState> {

    public AppComponent(UiRegistry uiRegistry, Router router, List<Module> modules, HttpRequest httpRequest) {
        super();
    }

    @Override
    public ComponentStateSupplier<AppComponentState> initStateSupplier() {
        return (_, _) -> new AppComponentState();
    }

    @Override
    public ComponentView<AppComponentState> componentView() {
        return _ -> _ -> new RoutingComponent();
    }

    public record AppComponentState() {
    }
}
