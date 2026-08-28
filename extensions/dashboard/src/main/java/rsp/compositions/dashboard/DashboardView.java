package rsp.compositions.dashboard;

import rsp.component.ComponentView;
import rsp.component.IntentDispatcher;

import static rsp.dsl.Html.*;

public class DashboardView implements ComponentView<DashboardView.DashboardState, Object> {
    private final DashboardRuntime runtime;

    public DashboardView(DashboardRuntime runtime) {
        this.runtime = java.util.Objects.requireNonNull(runtime, "runtime");
    }

    public record DashboardState(DashboardDefinition definition) {
        public DashboardState {
            java.util.Objects.requireNonNull(definition, "definition");
        }
    }

    @Override
    public rsp.component.View<DashboardState> resolve(IntentDispatcher<Object> intents) {
        return state -> section(attr("class", "dashboard-page"),
                h1(state.definition().title()),
                new DashboardGrid(state.definition(), runtime)
        );
    }
}
