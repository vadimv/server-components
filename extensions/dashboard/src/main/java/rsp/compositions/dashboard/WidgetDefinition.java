package rsp.compositions.dashboard;

import java.util.Map;

/** Immutable data that describes a widget without holding a component or service. */
public interface WidgetDefinition {
    String id();

    String title();

    String description();

    String kind();

    default Map<String, Object> metadata() {
        return Map.of();
    }
}
