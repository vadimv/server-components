package rsp.app.posts.components;

import rsp.app.posts.services.CommentRateStreamService;
import rsp.compositions.dashboard.DashboardWidget;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ComponentCompositeKey;
import rsp.component.StateUpdate;
import rsp.component.definitions.Component;
import rsp.dom.XmlNs;
import rsp.dsl.Definition;
import rsp.dsl.PlainTag;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final DateTimeFormatter LIVE_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final List<GraphSample> samples;
    private final CommentRateStreamService streamService;
    private final String description;
    private final String periodLabel;
    private final String unitLabel;
    private final String legendLabel;
    private final Map<ComponentCompositeKey, Runnable> subscriptions = new ConcurrentHashMap<>();

    public CommentsRateGraphWidget(final List<GraphSample> samples) {
        this(samples,
                null,
                "Static comments per hour trend",
                "Last 6 hours",
                "comments/hour",
                "Comments/hour");
    }

    private CommentsRateGraphWidget(final List<GraphSample> samples,
                                    final CommentRateStreamService streamService,
                                    final String description,
                                    final String periodLabel,
                                    final String unitLabel,
                                    final String legendLabel) {
        this.samples = samples == null ? List.of() : List.copyOf(samples);
        this.streamService = streamService;
        this.description = description;
        this.periodLabel = periodLabel;
        this.unitLabel = unitLabel;
        this.legendLabel = legendLabel;
    }

    public static CommentsRateGraphWidget live(final CommentRateStreamService streamService) {
        return new CommentsRateGraphWidget(List.of(),
                Objects.requireNonNull(streamService),
                "Live comments per second stream",
                "Last 30 seconds",
                "comments/sec",
                "Comments/sec");
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
        return description;
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
        State state = State.from(currentSamples());
        return Map.of("currentValue", state.currentValue(),
                "currentLabel", state.currentLabel(),
                "sampleCount", state.samples().size(),
                "empty", state.empty(),
                "live", streamService != null,
                "unit", unitLabel,
                "window", periodLabel);
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

    private record ChartRender(String linePath,
                               List<AxisTick> yTicks,
                               List<AxisLabel> xLabels) {
        private ChartRender {
            linePath = linePath == null || linePath.isBlank() ? fallbackLinePath() : linePath;
            yTicks = yTicks == null ? List.of() : List.copyOf(yTicks);
            xLabels = xLabels == null ? List.of() : List.copyOf(xLabels);
        }

        static ChartRender from(final List<GraphSample> samples) {
            List<GraphSample> safeSamples = samples == null ? List.of() : List.copyOf(samples);
            if (safeSamples.isEmpty()) {
                return empty();
            }

            Scale scale = Scale.from(safeSamples);
            List<ChartPoint> points = pointsFor(safeSamples, scale);
            return new ChartRender(
                    linePathFor(points),
                    yTicksFor(scale),
                    xLabelsFor(safeSamples, points));
        }

        static ChartRender empty() {
            return new ChartRender(
                    fallbackLinePath(),
                    yTicksFor(new Scale(0, 1, 0)),
                    List.of());
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
        return (_, _) -> State.from(currentSamples());
    }

    @Override
    public ComponentView<State> componentView() {
        return _ -> state -> div(attr("class", "dashboard-widget comments-rate-widget"),
                div(attr("class", "dashboard-widget-header"),
                        div(attr("class", "dashboard-widget-title"),
                                h2(title()),
                                p(periodLabel)
                        ),
                        div(attr("class", "dashboard-widget-metric"),
                                span(attr("class", "dashboard-widget-value"), text(state.currentValue())),
                                span(attr("class", "dashboard-widget-unit"), text(unitLabel))
                        )
                ),
                div(attr("class", "comments-rate-chart-wrap"),
                        chart(state)
                ),
                div(attr("class", "comments-rate-legend"),
                        span(attr("class", "comments-rate-legend-swatch"), attr("aria-hidden", "true")),
                        span(attr("class", "comments-rate-legend-label"), text(legendLabel)),
                        span(attr("class", "comments-rate-empty"), text(state.empty() ? "No data yet" : ""))
                )
        );
    }

    @Override
    public void onMounted(final ComponentCompositeKey componentId,
                          final State state,
                          final StateUpdate<State> stateUpdate) {
        if (streamService == null) {
            return;
        }
        subscriptions.computeIfAbsent(componentId, _ ->
                streamService.subscribe(streamSamples ->
                        stateUpdate.setState(State.from(graphSamplesFrom(streamSamples)))));
    }

    @Override
    public void onUnmounted(final ComponentCompositeKey componentId, final State state) {
        Runnable unsubscribe = subscriptions.remove(componentId);
        if (unsubscribe != null) {
            unsubscribe.run();
        }
    }

    private List<GraphSample> currentSamples() {
        if (streamService == null) {
            return samples;
        }
        return graphSamplesFrom(streamService.snapshot());
    }

    private static List<GraphSample> graphSamplesFrom(final List<CommentRateStreamService.Sample> samples) {
        return samples.stream()
                .map(sample -> new GraphSample(LIVE_LABEL_FORMATTER.format(sample.timestamp()), sample.value()))
                .toList();
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
                new PlainTag(XmlNs.svg, "path",
                        attr("class", "comments-rate-line"),
                        attr("d", state.chart().linePath()),
                        attr("fill", "none"),
                        attr("stroke", "currentColor"),
                        attr("stroke-width", "1.25"),
                        attr("stroke-linecap", "round"),
                        attr("stroke-linejoin", "round"))
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

    private static String linePathFor(final List<ChartPoint> chartPoints) {
        if (chartPoints.isEmpty()) {
            return fallbackLinePath();
        }
        if (chartPoints.size() == 1) {
            ChartPoint point = chartPoints.getFirst();
            double halfSegment = 8.0;
            return "M" + formatPoint(point.x() - halfSegment, point.y())
                    + " L" + formatPoint(point.x() + halfSegment, point.y());
        }

        StringBuilder path = new StringBuilder();
        ChartPoint first = chartPoints.getFirst();
        path.append("M").append(formatPoint(first.x(), first.y()));
        for (int i = 1; i < chartPoints.size(); i++) {
            ChartPoint p = chartPoints.get(i);
            path.append(" L").append(formatPoint(p.x(), p.y()));
        }
        return path.toString();
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
        if (samples.size() == 1) {
            return List.of(new AxisLabel(samples.getFirst().label(), points.getFirst().x()));
        }
        int middle = samples.size() / 2;
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

    private static String fallbackLinePath() {
        return "M" + formatPoint(PLOT_LEFT, PLOT_MIDLINE)
                + " L" + formatPoint(WIDTH - PLOT_RIGHT, PLOT_MIDLINE);
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
