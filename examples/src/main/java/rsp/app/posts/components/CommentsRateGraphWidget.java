package rsp.app.posts.components;

import rsp.app.posts.components.DashboardModel.GraphSample;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.dom.XmlNs;
import rsp.dsl.Definition;
import rsp.dsl.PlainTag;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static rsp.dsl.Html.*;

public class CommentsRateGraphWidget extends Component<CommentsRateGraphWidget.State>
        implements DashboardWidget {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 140;
    private static final int PADDING = 18;

    private final List<GraphSample> samples;

    public CommentsRateGraphWidget(final List<GraphSample> samples) {
        this.samples = samples == null ? List.of() : List.copyOf(samples);
    }

    @Override
    public String id() {
        return "comments-rate";
    }

    @Override
    public String title() {
        return "Comments rate";
    }

    @Override
    public String description() {
        return "Static comments per hour trend";
    }

    @Override
    public String kind() {
        return "line-chart";
    }

    @Override
    public Component<?> component() {
        return this;
    }

    @Override
    public Map<String, Object> metadataState() {
        State state = State.from(samples);
        return Map.of("currentValue", state.currentValue(),
                "currentLabel", state.currentLabel(),
                "sampleCount", state.samples().size(),
                "empty", state.empty());
    }

    public record State(List<GraphSample> samples,
                        String polylinePoints,
                        int currentValue,
                        String currentLabel,
                        boolean empty) {
        public State {
            samples = samples == null ? List.of() : List.copyOf(samples);
            polylinePoints = polylinePoints == null || polylinePoints.isBlank()
                    ? fallbackPolyline()
                    : polylinePoints;
            currentLabel = currentLabel == null ? "" : currentLabel;
        }

        static State from(final List<GraphSample> samples) {
            List<GraphSample> safeSamples = samples == null ? List.of() : List.copyOf(samples);
            if (safeSamples.isEmpty()) {
                return new State(List.of(), fallbackPolyline(), 0, "No data", true);
            }

            GraphSample current = safeSamples.getLast();
            return new State(safeSamples,
                    polylineFor(safeSamples),
                    current.value(),
                    current.label(),
                    false);
        }
    }

    @Override
    public ComponentStateSupplier<State> initStateSupplier() {
        return (_, _) -> State.from(samples);
    }

    @Override
    public ComponentView<State> componentView() {
        return _ -> state -> div(attr("class", "dashboard-widget comments-rate-widget"),
                div(attr("class", "dashboard-widget-header"),
                        div(attr("class", "dashboard-widget-title"),
                                h2(title()),
                                p("Last 6 hours")
                        ),
                        div(attr("class", "dashboard-widget-metric"),
                                span(attr("class", "dashboard-widget-value"), text(state.currentValue())),
                                span(attr("class", "dashboard-widget-unit"), text("comments/hour"))
                        )
                ),
                div(attr("class", "comments-rate-chart-wrap"),
                        chart(state)
                ),
                div(attr("class", "comments-rate-footer"),
                        span(attr("class", "comments-rate-current-label"), text(state.currentLabel())),
                        span(attr("class", "comments-rate-empty"), text(state.empty() ? "No data yet" : ""))
                )
        );
    }

    private static Definition chart(final State state) {
        return new PlainTag(XmlNs.svg, "svg",
                attr("class", "comments-rate-chart"),
                attr("viewBox", "0 0 " + WIDTH + " " + HEIGHT),
                attr("role", "img"),
                attr("aria-label", "Comments rate trend"),
                new PlainTag(XmlNs.svg, "title", text("Comments rate trend")),
                new PlainTag(XmlNs.svg, "line",
                        attr("class", "comments-rate-axis"),
                        attr("x1", String.valueOf(PADDING)),
                        attr("y1", String.valueOf(HEIGHT - PADDING)),
                        attr("x2", String.valueOf(WIDTH - PADDING)),
                        attr("y2", String.valueOf(HEIGHT - PADDING))),
                new PlainTag(XmlNs.svg, "polyline",
                        attr("class", "comments-rate-line"),
                        attr("points", state.polylinePoints()),
                        attr("fill", "none"),
                        attr("stroke", "currentColor"),
                        attr("stroke-width", "3"),
                        attr("stroke-linecap", "round"),
                        attr("stroke-linejoin", "round")),
                new PlainTag(XmlNs.svg, "circle",
                        attr("class", "comments-rate-point"),
                        attr("cx", lastPointX(state.samples())),
                        attr("cy", lastPointY(state.samples())),
                        attr("r", "4"))
        );
    }

    private static String polylineFor(final List<GraphSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return fallbackPolyline();
        }

        IntSummaryStatistics stats = samples.stream()
                .mapToInt(GraphSample::value)
                .summaryStatistics();
        double plotWidth = WIDTH - (PADDING * 2.0);
        double plotHeight = HEIGHT - (PADDING * 2.0);
        double min = stats.getMin();
        double max = stats.getMax();
        int lastIndex = samples.size() - 1;

        StringBuilder points = new StringBuilder();
        for (int i = 0; i < samples.size(); i++) {
            double x = lastIndex == 0
                    ? WIDTH / 2.0
                    : PADDING + ((plotWidth / lastIndex) * i);
            double normalized = max == min ? 0.5 : (samples.get(i).value() - min) / (max - min);
            double y = HEIGHT - PADDING - (normalized * plotHeight);

            if (!points.isEmpty()) {
                points.append(' ');
            }
            points.append(formatPoint(x, y));
        }
        return points.toString();
    }

    private static String lastPointX(final List<GraphSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return String.valueOf(WIDTH - PADDING);
        }
        if (samples.size() == 1) {
            return formatCoordinate(WIDTH / 2.0);
        }
        return formatCoordinate(WIDTH - PADDING);
    }

    private static String lastPointY(final List<GraphSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return formatCoordinate(HEIGHT / 2.0);
        }

        IntSummaryStatistics stats = samples.stream()
                .mapToInt(GraphSample::value)
                .summaryStatistics();
        double min = stats.getMin();
        double max = stats.getMax();
        double plotHeight = HEIGHT - (PADDING * 2.0);
        double current = samples.getLast().value();
        double normalized = max == min ? 0.5 : (current - min) / (max - min);
        return formatCoordinate(HEIGHT - PADDING - (normalized * plotHeight));
    }

    private static String fallbackPolyline() {
        return formatPoint(PADDING, HEIGHT / 2.0)
                + " "
                + formatPoint(WIDTH - PADDING, HEIGHT / 2.0);
    }

    private static String formatPoint(final double x, final double y) {
        return formatCoordinate(x) + "," + formatCoordinate(y);
    }

    private static String formatCoordinate(final double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
