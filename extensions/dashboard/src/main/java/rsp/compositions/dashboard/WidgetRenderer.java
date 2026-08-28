package rsp.compositions.dashboard;

import rsp.component.definitions.Component;

/** Materializes one immutable widget definition against dashboard runtime services. */
public interface WidgetRenderer<D extends WidgetDefinition> {
    Class<D> definitionType();

    Component<?, ?> render(D definition, DashboardRuntime runtime);
}
