package rsp.app.posts.components;

import rsp.app.posts.services.LogEntry;
import rsp.app.posts.services.LogStreamService;
import rsp.compositions.dashboard.DashboardWidget;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ComponentCompositeKey;
import rsp.component.StateUpdate;
import rsp.component.definitions.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static rsp.dsl.Html.*;

public class LogsWidget extends Component<LogsWidget.State>
        implements DashboardWidget {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Client-side helpers for the live logs viewport. Auto-follow keeps the view pinned to the
     * bottom while the user hasn't scrolled up. The connection indicator follows the browser-side
     * RSP connection lifecycle because the server cannot re-render the badge after a disconnect.
     */
    private static final String LOGS_CLIENT_SCRIPT = """
            (function(){
              function syncConnectionIndicators() {
                var state = document.body
                  ? document.body.getAttribute('data-rsp-connection')
                  : '';
                var label = state === 'closed'
                  ? 'Reconnecting'
                  : state === 'connecting'
                    ? 'Connecting'
                    : 'Live';
                var live = state !== 'closed' && state !== 'connecting';
                var statuses = document.querySelectorAll('.logs-status');
                for (var i = 0; i < statuses.length; i++) {
                  var status = statuses[i];
                  if (status.classList.contains('logs-status-static')) continue;
                  status.classList.toggle('logs-status-live', live);
                  status.classList.toggle('logs-status-lost', state === 'closed');
                  status.classList.toggle('logs-status-connecting', state === 'connecting');
                  status.textContent = label;
                }
              }
              function init() {
                var widget = document.querySelector('.logs-widget');
                if (!widget) return;
                if (!window.__logsConnectionIndicator) {
                  window.__logsConnectionIndicator = { sync: syncConnectionIndicators };
                  document.addEventListener('rsp:connection-state', function() {
                    window.__logsConnectionIndicator.sync();
                  });
                } else {
                  window.__logsConnectionIndicator.sync = syncConnectionIndicators;
                }
                window.__logsConnectionIndicator.sync();
                var container = widget.querySelector('.logs-content');
                if (!container) return;
                if (window.__logsScroller) {
                  if (window.__logsScroller.container === container) return;
                  window.__logsScroller.detach();
                }
                var BOTTOM_THRESHOLD = 8;
                var pinned = true;
                function isAtBottom() {
                  return container.scrollHeight - container.scrollTop - container.clientHeight <= BOTTOM_THRESHOLD;
                }
                function scrollToBottom() {
                  container.scrollTop = container.scrollHeight;
                }
                scrollToBottom();
                function onScroll() { pinned = isAtBottom(); }
                container.addEventListener('scroll', onScroll, { passive: true });
                var observer = new MutationObserver(function() {
                  if (pinned) scrollToBottom();
                });
                observer.observe(container, { childList: true });
                window.__logsScroller = {
                  container: container,
                  detach: function() {
                    container.removeEventListener('scroll', onScroll);
                    observer.disconnect();
                  }
                };
              }
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', init);
              } else {
                init();
              }
            })();
            """;

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
                div(attr("class", "dashboard-widget-header logs-widget-header"),
                        div(attr("class", "dashboard-widget-title"),
                                h2(title()),
                                p(periodLabel)
                        ),
                        div(attr("class", "logs-widget-meta"),
                                span(attr("class", streamService == null
                                                ? "logs-status logs-status-static"
                                                : "logs-status logs-status-live"),
                                        text(streamService == null ? "Sample" : "Live")),
                                span(attr("class", "logs-meta-item"), text(entryCountLabel(state.entries().size()))),
                                span(attr("class", "logs-meta-item"), text(lastEntryLabel(state.entries())))
                        )
                ),
                div(attr("class", "logs-content"),
                        state.empty()
                                ? div(attr("class", "logs-empty"), text("No log entries yet"))
                                : of(state.entries().stream().map(LogsWidget::logRow))
                ),
                streamService != null
                        ? script(text(LOGS_CLIENT_SCRIPT))
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

    private static String entryCountLabel(final int entryCount) {
        return entryCount + (entryCount == 1 ? " event" : " events");
    }

    private static String lastEntryLabel(final List<LogEntry> entries) {
        if (entries.isEmpty()) {
            return "No events";
        }
        LogEntry lastEntry = entries.getLast();
        return "Last " + TIME_FORMATTER.format(lastEntry.timestamp());
    }
}
