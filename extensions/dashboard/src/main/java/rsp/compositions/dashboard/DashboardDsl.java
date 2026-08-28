package rsp.compositions.dashboard;

import rsp.telemetry.TelemetryKey;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fluent Java DSL for immutable telemetry dashboards. */
public final class DashboardDsl {
    private DashboardDsl() {}

    public static DashboardBuilder dashboard(String id, String title) {
        return new DashboardBuilder(id, title);
    }

    public static GridAreaBuilder at(int column, int row) {
        return new GridAreaBuilder(column, row);
    }

    public static ValueBuilder value(String id) {
        return new ValueBuilder(id);
    }

    public static GaugeBuilder gauge(String id) {
        return new GaugeBuilder(id);
    }

    public static TrendBuilder trend(String id) {
        return new TrendBuilder(id);
    }

    public interface WidgetBuilder {
        WidgetDefinition build();
    }

    public static final class DashboardBuilder {
        private final String id;
        private final String title;
        private int columns = 12;
        private int rowHeightPx = 96;
        private String gap = "1rem";
        private final List<PlacedWidget> widgets = new ArrayList<>();

        private DashboardBuilder(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public DashboardBuilder columns(int columns) {
            this.columns = columns;
            return this;
        }

        public DashboardBuilder rowHeightPx(int rowHeightPx) {
            this.rowHeightPx = rowHeightPx;
            return this;
        }

        public DashboardBuilder gap(String gap) {
            this.gap = gap;
            return this;
        }

        public DashboardBuilder place(WidgetBuilder widget, GridArea area) {
            return place(Objects.requireNonNull(widget, "widget").build(), area);
        }

        public DashboardBuilder place(WidgetDefinition widget, GridArea area) {
            widgets.add(new PlacedWidget(widget, area));
            return this;
        }

        public DashboardDefinition build() {
            return new DashboardDefinition(id, title,
                    new GridDefinition(columns, rowHeightPx, gap), widgets);
        }
    }

    public record GridAreaBuilder(int column, int row) {
        public GridArea span(int columnSpan, int rowSpan) {
            return new GridArea(column, row, columnSpan, rowSpan);
        }
    }

    public abstract static class BaseWidgetBuilder<B extends BaseWidgetBuilder<B>> {
        final String id;
        String title;
        String description = "";

        BaseWidgetBuilder(String id) {
            this.id = id;
            this.title = id;
        }

        public B title(String title) {
            this.title = title;
            return self();
        }

        public B description(String description) {
            this.description = description;
            return self();
        }

        abstract B self();
    }

    public static final class ValueBuilder extends BaseWidgetBuilder<ValueBuilder>
            implements WidgetBuilder {
        private TelemetryKey<Double> value;
        private int decimals = 1;
        private Duration staleAfter = Duration.ofSeconds(30);

        private ValueBuilder(String id) {
            super(id);
        }

        public ValueBuilder value(TelemetryKey<Double> value) {
            this.value = value;
            return this;
        }

        public ValueBuilder decimals(int decimals) {
            this.decimals = decimals;
            return this;
        }

        public ValueBuilder staleAfter(Duration staleAfter) {
            this.staleAfter = staleAfter;
            return this;
        }

        @Override
        public ValueDefinition build() {
            return new ValueDefinition(id, title, description, value, decimals, staleAfter);
        }

        @Override
        ValueBuilder self() {
            return this;
        }
    }

    public static final class GaugeBuilder extends BaseWidgetBuilder<GaugeBuilder>
            implements WidgetBuilder {
        private TelemetryKey<Double> value;
        private double minimum;
        private double maximum = 100;
        private int decimals = 1;
        private Duration staleAfter = Duration.ofSeconds(30);

        private GaugeBuilder(String id) {
            super(id);
        }

        public GaugeBuilder value(TelemetryKey<Double> value) {
            this.value = value;
            return this;
        }

        public GaugeBuilder range(double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
            return this;
        }

        public GaugeBuilder decimals(int decimals) {
            this.decimals = decimals;
            return this;
        }

        public GaugeBuilder staleAfter(Duration staleAfter) {
            this.staleAfter = staleAfter;
            return this;
        }

        @Override
        public GaugeDefinition build() {
            return new GaugeDefinition(id, title, description, value, minimum, maximum,
                    decimals, staleAfter);
        }

        @Override
        GaugeBuilder self() {
            return this;
        }
    }

    public static final class TrendBuilder extends BaseWidgetBuilder<TrendBuilder>
            implements WidgetBuilder {
        private TelemetryKey<Double> series;
        private Duration window = Duration.ofMinutes(5);
        private int decimals = 1;

        private TrendBuilder(String id) {
            super(id);
        }

        public TrendBuilder series(TelemetryKey<Double> series) {
            this.series = series;
            return this;
        }

        public TrendBuilder window(Duration window) {
            this.window = window;
            return this;
        }

        public TrendBuilder decimals(int decimals) {
            this.decimals = decimals;
            return this;
        }

        @Override
        public TrendDefinition build() {
            return new TrendDefinition(id, title, description, series, window, decimals);
        }

        @Override
        TrendBuilder self() {
            return this;
        }
    }
}
