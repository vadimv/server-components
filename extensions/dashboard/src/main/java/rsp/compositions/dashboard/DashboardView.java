package rsp.compositions.dashboard;

import rsp.component.ComponentView;
import rsp.component.IntentDispatcher;

import static rsp.dsl.Html.*;

public class DashboardView implements ComponentView<DashboardView.DashboardState, Object> {

    public record DashboardState(DashboardModel model) {
        public DashboardState {
            model = model == null ? new DashboardModel(null) : model;
        }
    }

    @Override
    public rsp.component.View<DashboardState> resolve(IntentDispatcher<Object> intents) {
        return state -> section(attr("class", "dashboard-page"),
                h1("Dashboard"),
                new DashboardGrid(state.model().layout())
        );
    }
}
