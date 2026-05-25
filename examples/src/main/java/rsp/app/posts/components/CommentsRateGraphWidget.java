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

    private static final int WIDTH = 360;
    private static final int HEIGHT = 180;
    private static final int PLOT_LEFT = 42;
    private static final int PLOT_RIGHT = 16;
    private static final int PLOT_TOP = 16;
    private static final int PLOT_BOTTOM = 34;
    private static final double PLOT_WIDTH = WIDTH - PLOT_LEFT - PLOT_RIGHT;
    private static final double PLOT_HEIGHT = HEIGHT - PLOT_TOP - PLOT_BOTTOM;
    private static final double PLOT_BASELINE = HEIGHT - PLOT_BOTTOM;
    private static final double PLOT_MIDLINE = PLOT_TOP + (PLOT_HEIGHT / 2.0);

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
                        ChartRender chart,
                        int currentValue,
                        String currentLabel,
                        boolean empty) {
        public State {
            samples = samples == null ? List.of() : List.copyOf(samples);
            chart = chart == null ? ChartRender.empty() : chart;
            currentLabel = currentLabel == null ? "" : currentLabel;
        }

        static State from(final List<GraphSample> samples) {
            List<GraphSample> safeSamples = samples == null ? List.of() : List.copyOf(samples);
            if (safeSamples.isEmpty()) {
                return new State(List.of(), ChartRender.empty(), 0, "No data", true);
            }

            GraphSample current = safeSamples.getLast();
            return new State(safeSamples,
                    ChartRender.from(safeSamples),
                    current.value(),
                    current.label(),
                    false);
        }
    }

    private record ChartRender(String polylinePoints,
                               List<AxisTick> yTicks,
                               List<AxisLabel> xLabels,
                               String currentPointX,
                               String currentPointY) {
        private ChartRender {
            polylinePoints = polylinePoints == null || polylinePoints.isBlank()
                    ? fallbackPolyline()
                    : polylinePoints;
            yTicks = yTicks == null ? List.of() : List.copyOf(yTicks);
            xLabels = xLabels == null ? List.of() : List.copyOf(xLabels);
            currentPointX = currentPointX == null ? formatCoordinate(WIDTH - PLOT_RIGHT) : currentPointX;
            currentPointY = currentPointY == null ? formatCoordinate(PLOT_MIDLINE) : currentPointY;
        }

        static ChartRender from(final List<GraphSample> samples) {
            List<GraphSample> safeSamples = samples == null ? List.of() : List.copyOf(samples);
            if (safeSamples.isEmpty()) {
                return empty();
            }

            Scale scale = Scale.from(safeSamples);
            List<ChartPoint> points = pointsFor(safeSamples, scale);
            ChartPoint current = points.getLast();
            return new ChartRender(
                    polylineFor(points),
                    yTicksFor(scale),
                    xLabelsFor(safeSamples, points),
                    formatCoordinate(current.x()),
                    formatCoordinate(current.y()));
        }

        static ChartRender empty() {
            return new ChartRender(
                    fallbackPolyline(),
                    yTicksFor(new Scale(0, 1, 0)),
                    List.of(),
                    formatCoordinate(WIDTH - PLOT_RIGHT),
                    formatCoordinate(PLOT_MIDLINE));
        }
    }

    private record Scale(double min, double max, double flatValue) {
        static Scale from(final List<GraphSample> samples) {
            IntSummaryStatistics stats = samples.stream()
                    .mapToInt(GraphSample::value)
                    .summaryStatistics();
            double min = stats.getMin();
            double max = stats.getMax();
            double flatValue = min == max ? min : Double.NaN;
            if (min == max) {
                min = Math.max(0, min - 1);
                max = max + 1;
            }
            return new Scale(min, max, flatValue);
        }

        boolean flat() {
            return !Double.isNaN(flatValue);
        }
    }

    private record ChartPoint(double x, double y) {}

    private record AxisTick(String label, double y) {}

    private record AxisLabel(String label, double x) {}

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
                div(attr("class", "comments-rate-legend"),
                        span(attr("class", "comments-rate-legend-swatch"), attr("aria-hidden", "true")),
                        span(attr("class", "comments-rate-legend-label"), text("Comments/hour"))
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
                of(state.chart().yTicks().stream().map(CommentsRateGraphWidget::gridLine)),
                of(state.chart().yTicks().stream().map(CommentsRateGraphWidget::tickLabel)),
                of(state.chart().xLabels().stream().map(CommentsRateGraphWidget::xAxisLabel)),
                new PlainTag(XmlNs.svg, "line",
                        attr("class", "comments-rate-axis comments-rate-y-axis"),
                        attr("x1", String.valueOf(PLOT_LEFT)),
                        attr("y1", String.valueOf(PLOT_TOP)),
                        attr("x2", String.valueOf(PLOT_LEFT)),
                        attr("y2", String.valueOf(PLOT_BASELINE))),
                new PlainTag(XmlNs.svg, "line",
                        attr("class", "comments-rate-axis comments-rate-x-axis"),
                        attr("x1", String.valueOf(PLOT_LEFT)),
                        attr("y1", String.valueOf(PLOT_BASELINE)),
                        attr("x2", String.valueOf(WIDTH - PLOT_RIGHT)),
                        attr("y2", String.valueOf(PLOT_BASELINE))),
                new PlainTag(XmlNs.svg, "polyline",
                        attr("class", "comments-rate-line"),
                        attr("points", state.chart().polylinePoints()),
                        attr("fill", "none"),
                        attr("stroke", "currentColor"),
                        attr("stroke-width", "3"),
                        attr("stroke-linecap", "round"),
                        attr("stroke-linejoin", "round")),
                new PlainTag(XmlNs.svg, "circle",
                        attr("class", "comments-rate-point"),
                        attr("cx", state.chart().currentPointX()),
                        attr("cy", state.chart().currentPointY()),
                        attr("r", "4"))
        );
    }

    private static Definition gridLine(final AxisTick tick) {
        return new PlainTag(XmlNs.svg, "line",
                attr("class", "comments-rate-grid-line"),
                attr("x1", String.valueOf(PLOT_LEFT)),
                attr("y1", formatCoordinate(tick.y())),
                attr("x2", String.valueOf(WIDTH - PLOT_RIGHT)),
                attr("y2", formatCoordinate(tick.y())));
    }

    private static Definition tickLabel(final AxisTick tick) {
        return new PlainTag(XmlNs.svg, "text",
                attr("class", "comments-rate-axis-label comments-rate-tick-label"),
                attr("x", String.valueOf(PLOT_LEFT - 8)),
                attr("y", formatCoordinate(tick.y())),
                attr("text-anchor", "end"),
                attr("dominant-baseline", "middle"),
                text(tick.label()));
    }

    private static Definition xAxisLabel(final AxisLabel label) {
        return new PlainTag(XmlNs.svg, "text",
                attr("class", "comments-rate-axis-label comments-rate-x-label"),
                attr("x", formatCoordinate(label.x())),
                attr("y", String.valueOf(HEIGHT - 10)),
                attr("text-anchor", "middle"),
                text(label.label()));
    }

    private static List<ChartPoint> pointsFor(final List<GraphSample> samples, final Scale scale) {
        int lastIndex = samples.size() - 1;
        return java.util.stream.IntStream.range(0, samples.size())
                .mapToObj(i -> {
                    double x = lastIndex == 0
                            ? PLOT_LEFT + (PLOT_WIDTH / 2.0)
                            : PLOT_LEFT + ((PLOT_WIDTH / lastIndex) * i);
                    double y = scale.flat()
                            ? PLOT_MIDLINE
                            : yFor(samples.get(i).value(), scale);
                    return new ChartPoint(x, y);
                })
                .toList();
    }

    private static String polylineFor(final List<ChartPoint> chartPoints) {
        StringBuilder points = new StringBuilder();
        for (ChartPoint point : chartPoints) {
            if (!points.isEmpty()) {
                points.append(' ');
            }
            points.append(formatPoint(point.x(), point.y()));
        }
        return points.toString();
    }

    private static List<AxisTick> yTicksFor(final Scale scale) {
        double topValue = scale.max();
        double middleValue = scale.flat() ? scale.flatValue() : scale.min() + ((scale.max() - scale.min()) / 2.0);
        double bottomValue = scale.min();
        return List.of(
                new AxisTick(formatTick(topValue), yFor(topValue, scale)),
                new AxisTick(formatTick(middleValue), scale.flat() ? PLOT_MIDLINE : yFor(middleValue, scale)),
                new AxisTick(formatTick(bottomValue), yFor(bottomValue, scale))
        );
    }

    private static List<AxisLabel> xLabelsFor(final List<GraphSample> samples, final List<ChartPoint> points) {
        if (samples.isEmpty() || points.isEmpty()) {
            return List.of();
        }
        int middle = samples.size() / 2;
        if (samples.size() == 1) {
            return List.of(new AxisLabel(samples.getFirst().label(), points.getFirst().x()));
        }

        return List.of(
                new AxisLabel(samples.getFirst().label(), points.getFirst().x()),
                new AxisLabel(samples.get(middle).label(), points.get(middle).x()),
                new AxisLabel(samples.getLast().label(), points.getLast().x())
        );
    }

    private static double yFor(final double value, final Scale scale) {
        double normalized = (value - scale.min()) / (scale.max() - scale.min());
        return PLOT_BASELINE - (normalized * PLOT_HEIGHT);
    }

    private static String fallbackPolyline() {
        return formatPoint(PLOT_LEFT, PLOT_MIDLINE)
                + " "
                + formatPoint(WIDTH - PLOT_RIGHT, PLOT_MIDLINE);
    }

    private static String formatTick(final double value) {
        if (Math.rint(value) == value) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatPoint(final double x, final double y) {
        return formatCoordinate(x) + "," + formatCoordinate(y);
    }

    private static String formatCoordinate(final double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
