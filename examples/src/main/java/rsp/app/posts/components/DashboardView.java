package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;

import static rsp.dsl.Html.*;

public class DashboardView extends Component<DashboardView.DashboardState> {

    public record DashboardState(DashboardModel model) {
        public DashboardState {
            model = model == null ? DashboardModel.demo() : model;
        }
    }

    @Override
    public ComponentStateSupplier<DashboardState> initStateSupplier() {
        return (_, context) -> {
            DashboardModel model = context.get(DashboardContract.DASHBOARD_MODEL);
            return new DashboardState(model);
        };
    }

    @Override
    public ComponentView<DashboardState> componentView() {
        return _ -> state -> section(attr("class", "dashboard-page"),
                h1("Dashboard"),
                new DashboardGrid(state.model().layout())
        );
    }
}
