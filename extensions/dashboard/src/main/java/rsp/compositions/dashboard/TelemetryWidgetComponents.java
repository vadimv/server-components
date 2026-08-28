package rsp.compositions.dashboard;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.component.definitions.Component;
import rsp.dom.XmlNs;
import rsp.dsl.Definition;
import rsp.dsl.PlainTag;
import rsp.telemetry.Subscription;
import rsp.telemetry.Telemetry;
import rsp.telemetry.TelemetryQuality;
import rsp.telemetry.TelemetrySample;
import rsp.telemetry.TelemetrySeries;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static rsp.dsl.Html.attr;
import static rsp.dsl.Html.div;
import static rsp.dsl.Html.h2;
import static rsp.dsl.Html.span;
import static rsp.dsl.Html.text;

final class TelemetryWidgetComponents {
    private TelemetryWidgetComponents() {}

    static WidgetRenderer<ValueDefinition> valueRenderer() {
        return renderer(ValueDefinition.class,
                (definition, runtime) -> new ValueComponent(definition, runtime));
    }

    static WidgetRenderer<GaugeDefinition> gaugeRenderer() {
        return renderer(GaugeDefinition.class,
                (definition, runtime) -> new GaugeComponent(definition, runtime));
    }

    static WidgetRenderer<TrendDefinition> trendRenderer() {
        return renderer(TrendDefinition.class,
                (definition, runtime) -> new TrendComponent(definition, runtime));
    }

    private static <D extends WidgetDefinition> WidgetRenderer<D> renderer(
            Class<D> type,
            java.util.function.BiFunction<D, DashboardRuntime, Component<?, ?>> factory) {
        return new WidgetRenderer<>() {
            @Override
            public Class<D> definitionType() {
                return type;
            }

            @Override
            public Component<?, ?> render(D definition, DashboardRuntime runtime) {
                return factory.apply(definition, runtime);
            }
        };
    }

    private abstract static class ScalarComponent<D extends WidgetDefinition>
            extends Component<Optional<TelemetrySample<Double>>, Object> {
        final D definition;
        final Clock clock;
        private final Telemetry<Double> source;
        private final Map<ComponentCompositeKey, Subscription> subscriptions = new ConcurrentHashMap<>();

        ScalarComponent(D definition, Telemetry<Double> source, Clock clock) {
            super(definition.id());
            this.definition = definition;
            this.source = source;
            this.clock = clock;
        }

        @Override
        public ComponentStateSupplier<Optional<TelemetrySample<Double>>> initStateSupplier() {
            return (_, _) -> source.snapshot();
        }

        @Override
        public void onMounted(ComponentCompositeKey componentId,
                              Optional<TelemetrySample<Double>> state,
                              StateUpdater<Optional<TelemetrySample<Double>>> stateUpdate) {
            subscriptions.computeIfAbsent(componentId,
                    _ -> source.subscribe(sample -> stateUpdate.setState(Optional.of(sample))));
        }

        @Override
        public void onUnmounted(ComponentCompositeKey componentId,
                                Optional<TelemetrySample<Double>> state) {
            Subscription subscription = subscriptions.remove(componentId);
            if (subscription != null) {
                subscription.close();
            }
        }

        String statusClass(Optional<TelemetrySample<Double>> sample, java.time.Duration staleAfter) {
            if (sample.isEmpty()) {
                return "telemetry-missing";
            }
            if (sample.get().isStale(staleAfter, clock)) {
                return "telemetry-stale";
            }
            return "telemetry-" + sample.get().quality().name().toLowerCase(Locale.ROOT);
        }

        String formatted(Optional<TelemetrySample<Double>> sample, int decimals) {
            return sample.map(value -> format(value.value(), decimals)).orElse("—");
        }
    }

    private static final class ValueComponent extends ScalarComponent<ValueDefinition> {
        ValueComponent(ValueDefinition definition, DashboardRuntime runtime) {
            super(definition, runtime.telemetry().resolve(definition.value()), runtime.clock());
        }

        @Override
        public ComponentView<Optional<TelemetrySample<Double>>, Object> componentView() {
            return _ -> state -> div(
                    attr("class", "dashboard-widget value-widget "
                            + statusClass(state, definition.staleAfter())),
                    h2(definition.title()),
                    div(attr("class", "dashboard-widget-metric"),
                            span(attr("class", "dashboard-widget-value"),
                                    text(formatted(state, definition.decimals()))),
                            span(attr("class", "dashboard-widget-unit"),
                                    text(definition.value().unit())))
            );
        }
    }

    private static final class GaugeComponent extends ScalarComponent<GaugeDefinition> {
        GaugeComponent(GaugeDefinition definition, DashboardRuntime runtime) {
            super(definition, runtime.telemetry().resolve(definition.value()), runtime.clock());
        }

        @Override
        public ComponentView<Optional<TelemetrySample<Double>>, Object> componentView() {
            return _ -> state -> {
                double percentage = state.map(TelemetrySample::value)
                        .map(value -> 100 * (value - definition.minimum())
                                / (definition.maximum() - definition.minimum()))
                        .map(value -> Math.max(0, Math.min(100, value)))
                        .orElse(0.0);
                return div(attr("class", "dashboard-widget gauge-widget "
                                + statusClass(state, definition.staleAfter())),
                        h2(definition.title()),
                        div(attr("class", "dashboard-widget-metric"),
                                span(attr("class", "dashboard-widget-value"),
                                        text(formatted(state, definition.decimals()))),
                                span(attr("class", "dashboard-widget-unit"),
                                        text(definition.value().unit()))),
                        div(attr("class", "gauge-track"),
                                div(attr("class", "gauge-fill"),
                                        attr("style", "width: " + format(percentage, 2) + "%"))),
                        div(attr("class", "gauge-range"),
                                span(text(format(definition.minimum(), definition.decimals()))),
                                span(text(format(definition.maximum(), definition.decimals()))))
                );
            };
        }
    }

    private static final class TrendComponent
            extends Component<List<TelemetrySample<Double>>, Object> {
        private final TrendDefinition definition;
        private final Clock clock;
        private final TelemetrySeries<Double> source;
        private final Map<ComponentCompositeKey, Subscription> subscriptions = new ConcurrentHashMap<>();

        TrendComponent(TrendDefinition definition, DashboardRuntime runtime) {
            super(definition.id());
            this.definition = definition;
            this.clock = runtime.clock();
            this.source = runtime.telemetry().resolveSeries(definition.series());
        }

        @Override
        public ComponentStateSupplier<List<TelemetrySample<Double>>> initStateSupplier() {
            return (_, _) -> visible(source.snapshot());
        }

        @Override
        public ComponentView<List<TelemetrySample<Double>>, Object> componentView() {
            return _ -> state -> div(attr("class", "dashboard-widget trend-widget " + qualityClass(state)),
                    div(attr("class", "dashboard-widget-header"),
                            h2(definition.title()),
                            div(attr("class", "dashboard-widget-metric"),
                                    span(attr("class", "dashboard-widget-value"),
                                            text(state.isEmpty() ? "—"
                                                    : format(state.getLast().value(), definition.decimals()))),
                                    span(attr("class", "dashboard-widget-unit"),
                                            text(definition.series().unit())))),
                    chart(state));
        }

        @Override
        public void onMounted(ComponentCompositeKey componentId,
                              List<TelemetrySample<Double>> state,
                              StateUpdater<List<TelemetrySample<Double>>> stateUpdate) {
            subscriptions.computeIfAbsent(componentId,
                    _ -> source.subscribe(samples -> stateUpdate.setState(visible(samples))));
        }

        @Override
        public void onUnmounted(ComponentCompositeKey componentId,
                                List<TelemetrySample<Double>> state) {
            Subscription subscription = subscriptions.remove(componentId);
            if (subscription != null) {
                subscription.close();
            }
        }

        private List<TelemetrySample<Double>> visible(List<TelemetrySample<Double>> samples) {
            Instant threshold = clock.instant().minus(definition.window());
            return samples.stream()
                    .filter(sample -> !sample.timestamp().isBefore(threshold))
                    .toList();
        }

        private static String qualityClass(List<TelemetrySample<Double>> samples) {
            if (samples.isEmpty()) {
                return "telemetry-missing";
            }
            TelemetryQuality quality = samples.getLast().quality();
            return "telemetry-" + quality.name().toLowerCase(Locale.ROOT);
        }

        private static Definition chart(List<TelemetrySample<Double>> samples) {
            return new PlainTag(XmlNs.svg, "svg",
                    attr("class", "telemetry-trend-chart"),
                    attr("viewBox", "0 0 100 40"),
                    attr("role", "img"),
                    attr("aria-label", "Telemetry trend"),
                    new PlainTag(XmlNs.svg, "path",
                            attr("d", trendPath(samples)),
                            attr("fill", "none"),
                            attr("stroke", "currentColor"),
                            attr("stroke-width", "1.5"),
                            attr("vector-effect", "non-scaling-stroke")));
        }
    }

    private static String trendPath(List<TelemetrySample<Double>> samples) {
        if (samples.isEmpty()) {
            return "";
        }
        double minimum = samples.stream().mapToDouble(TelemetrySample::value).min().orElse(0);
        double maximum = samples.stream().mapToDouble(TelemetrySample::value).max().orElse(1);
        double range = maximum == minimum ? 1 : maximum - minimum;
        int last = samples.size() - 1;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < samples.size(); index++) {
            double x = last == 0 ? 50 : 100.0 * index / last;
            double y = maximum == minimum ? 20 : 38 - (36 * (samples.get(index).value() - minimum) / range);
            result.append(index == 0 ? "M" : " L")
                    .append(format(x, 2)).append(' ').append(format(y, 2));
        }
        return result.toString();
    }

    private static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
