package rsp.app.posts.components;

import rsp.app.posts.services.LogEntry;
import rsp.app.posts.services.LogStreamService;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ComponentCompositeKey;
import rsp.component.StateUpdate;
import rsp.component.definitions.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static rsp.dsl.Html.*;

public class LogsWidget extends Component<LogsWidget.State>
        implements DashboardWidget {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final List<LogEntry> staticEntries;
    private final LogStreamService streamService;
    private final String description;
    private final String periodLabel;
    private final Map<ComponentCompositeKey, Runnable> subscriptions = new ConcurrentHashMap<>();

    public LogsWidget(final List<LogEntry> staticEntries) {
        this(staticEntries, null, "Static log entries", "Sample");
    }

    private LogsWidget(final List<LogEntry> staticEntries,
                       final LogStreamService streamService,
                       final String description,
                       final String periodLabel) {
        this.staticEntries = staticEntries == null ? List.of() : List.copyOf(staticEntries);
        this.streamService = streamService;
        this.description = description;
        this.periodLabel = periodLabel;
    }

    public static LogsWidget live(final LogStreamService streamService) {
        return new LogsWidget(List.of(),
                Objects.requireNonNull(streamService),
                "Live application log stream",
                "Live stream");
    }

    @Override
    public String id() {
        return "logs";
    }

    @Override
    public String title() {
        return "Logs";
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String kind() {
        return "log-stream";
    }

    @Override
    public Component<?> component() {
        return this;
    }

    @Override
    public Map<String, Object> metadataState() {
        State state = State.from(currentEntries());
        return Map.of("entryCount", state.entries().size(),
                "live", streamService != null,
                "window", periodLabel);
    }

    public record State(List<LogEntry> entries, boolean empty) {
        public State {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        static State from(final List<LogEntry> entries) {
            List<LogEntry> safe = entries == null ? List.of() : List.copyOf(entries);
            return new State(safe, safe.isEmpty());
        }
    }

    @Override
    public ComponentStateSupplier<State> initStateSupplier() {
        return (_, _) -> State.from(currentEntries());
    }

    @Override
    public ComponentView<State> componentView() {
        return _ -> state -> div(attr("class", "dashboard-widget logs-widget"),
                div(attr("class", "dashboard-widget-header"),
                        div(attr("class", "dashboard-widget-title"),
                                h2(title()),
                                p(periodLabel)
                        )
                ),
                div(attr("class", "logs-content"),
                        of(state.entries().stream().map(LogsWidget::logRow))
                ),
                state.empty()
                        ? div(attr("class", "logs-empty"), text("No log entries yet"))
                        : text("")
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
                streamService.subscribe(entries ->
                        stateUpdate.setState(State.from(entries))));
    }

    @Override
    public void onUnmounted(final ComponentCompositeKey componentId, final State state) {
        Runnable unsubscribe = subscriptions.remove(componentId);
        if (unsubscribe != null) {
            unsubscribe.run();
        }
    }

    private List<LogEntry> currentEntries() {
        if (streamService == null) {
            return staticEntries;
        }
        return streamService.snapshot();
    }

    private static rsp.dsl.Definition logRow(final LogEntry entry) {
        String levelClass = switch (entry.level()) {
            case INFO -> "logs-row-info";
            case WARN -> "logs-row-warn";
            case ERROR -> "logs-row-error";
        };
        return div(key(entry.sequence()),
                attr("class", "logs-row " + levelClass),
                span(attr("class", "logs-ts"), text(TIME_FORMATTER.format(entry.timestamp()))),
                span(attr("class", "logs-lvl"), text(entry.level().name())),
                span(attr("class", "logs-msg"), text(entry.message()))
        );
    }

    @SuppressWarnings("unused")
    private static String formatLevel(final LogEntry.Level level) {
        return String.format(Locale.ROOT, "%-5s", level.name());
    }
}
