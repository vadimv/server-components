package rsp.compositions.dashboard;

import rsp.component.definitions.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable mapping from widget definition types to their runtime renderers. */
public final class WidgetRendererRegistry {
    private final Map<Class<? extends WidgetDefinition>, WidgetRenderer<?>> renderers;

    private WidgetRendererRegistry(Map<Class<? extends WidgetDefinition>, WidgetRenderer<?>> renderers) {
        this.renderers = Map.copyOf(renderers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static WidgetRendererRegistry defaults() {
        return builder().registerDefaults().build();
    }

    public Component<?, ?> render(WidgetDefinition definition, DashboardRuntime runtime) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(runtime, "runtime");
        WidgetRenderer<?> renderer = renderers.get(definition.getClass());
        if (renderer == null) {
            throw new IllegalArgumentException(
                    "No widget renderer registered for " + definition.getClass().getName());
        }
        return renderTyped(renderer, definition, runtime);
    }

    private static <D extends WidgetDefinition> Component<?, ?> renderTyped(WidgetRenderer<D> renderer,
                                                                            WidgetDefinition definition,
                                                                            DashboardRuntime runtime) {
        return renderer.render(renderer.definitionType().cast(definition), runtime);
    }

    public static final class Builder {
        private final Map<Class<? extends WidgetDefinition>, WidgetRenderer<?>> renderers =
                new LinkedHashMap<>();

        public Builder registerDefaults() {
            return register(TelemetryWidgetComponents.valueRenderer())
                    .register(TelemetryWidgetComponents.gaugeRenderer())
                    .register(TelemetryWidgetComponents.trendRenderer());
        }

        public Builder register(WidgetRenderer<?> renderer) {
            Objects.requireNonNull(renderer, "renderer");
            if (renderers.putIfAbsent(renderer.definitionType(), renderer) != null) {
                throw new IllegalArgumentException(
                        "Duplicate widget renderer for " + renderer.definitionType().getName());
            }
            return this;
        }

        public WidgetRendererRegistry build() {
            return new WidgetRendererRegistry(renderers);
        }
    }
}
