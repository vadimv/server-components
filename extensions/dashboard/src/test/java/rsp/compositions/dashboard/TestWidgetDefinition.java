package rsp.compositions.dashboard;

import rsp.component.definitions.StatelessComponent;

import java.util.Map;

import static rsp.dsl.Html.*;

record TestWidgetDefinition(String id) implements WidgetDefinition {
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
    public Map<String, Object> metadata() {
        return Map.of("value", id == null ? 0 : id.length());
    }

    static WidgetRenderer<TestWidgetDefinition> renderer() {
        return new WidgetRenderer<>() {
            @Override
            public Class<TestWidgetDefinition> definitionType() {
                return TestWidgetDefinition.class;
            }

            @Override
            public rsp.component.definitions.Component<?, ?> render(
                    TestWidgetDefinition definition,
                    DashboardRuntime runtime) {
                return new StatelessComponent(definition.id(), _ -> _ ->
                        div(attr("class", "test-widget"), text(definition.title())));
            }
        };
    }
}
