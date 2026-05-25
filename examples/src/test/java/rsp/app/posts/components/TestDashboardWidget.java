package rsp.app.posts.components;

import rsp.component.definitions.Component;
import rsp.component.definitions.StatelessComponent;

import java.util.Map;

import static rsp.dsl.Html.*;

final class TestDashboardWidget implements DashboardWidget {
    private final String id;

    TestDashboardWidget(final String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String title() {
        return "Widget " + id;
    }

    @Override
    public String description() {
        return "Test widget " + id;
    }

    @Override
    public String kind() {
        return "test-widget";
    }

    @Override
    public Component<?> component() {
        return new StatelessComponent(id, _ -> _ ->
                div(attr("class", "test-widget"), text(title())));
    }

    @Override
    public Map<String, Object> metadataState() {
        return Map.of("value", id == null ? 0 : id.length());
    }
}
