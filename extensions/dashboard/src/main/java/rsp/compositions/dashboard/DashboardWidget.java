package rsp.compositions.dashboard;

import rsp.component.definitions.Component;

import java.util.Map;

public interface DashboardWidget {
    String id();

    String title();

    String description();

    String kind();

    Component<?> component();

    Map<String, Object> metadataState();
}
