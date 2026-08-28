package rsp.app.posts.components;

import rsp.app.posts.services.LogEntry;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ComponentCompositeKey;
import rsp.component.StateUpdater;
import rsp.component.definitions.Component;
import rsp.compositions.dashboard.DashboardRuntime;
import rsp.compositions.dashboard.WidgetRenderer;
import rsp.telemetry.Subscription;
import rsp.telemetry.TelemetrySample;
import rsp.telemetry.TelemetrySeries;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static rsp.dsl.Html.*;

public class LogsWidget extends Component<LogsWidget.State, Object> {

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

    private final LogsDefinition definition;
    private final TelemetrySeries<LogEntry> source;
    private final java.util.Map<ComponentCompositeKey, Subscription> subscriptions =
            new ConcurrentHashMap<>();

    public LogsWidget(LogsDefinition definition, DashboardRuntime runtime) {
        super(definition.id());
        this.definition = java.util.Objects.requireNonNull(definition, "definition");
        this.source = java.util.Objects.requireNonNull(runtime, "runtime")
                .telemetry().resolveSeries(definition.series());
    }

    public static WidgetRenderer<LogsDefinition> renderer() {
        return new WidgetRenderer<>() {
            @Override
            public Class<LogsDefinition> definitionType() {
                return LogsDefinition.class;
            }

            @Override
            public Component<?, ?> render(LogsDefinition definition, DashboardRuntime runtime) {
                return new LogsWidget(definition, runtime);
            }
        };
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
        return (_, _) -> State.from(entries(source.snapshot()));
    }

    @Override
    public ComponentView<State, Object> componentView() {
        return _ -> state -> div(attr("class", "dashboard-widget logs-widget"),
                div(attr("class", "dashboard-widget-header logs-widget-header"),
                        div(attr("class", "dashboard-widget-title"),
                                h2(definition.title()),
                                p("Live stream")
                        ),
                        div(attr("class", "logs-widget-meta"),
                                span(attr("class", "logs-status logs-status-live"), text("Live")),
                                span(attr("class", "logs-meta-item"), text(entryCountLabel(state.entries().size()))),
                                span(attr("class", "logs-meta-item"), text(lastEntryLabel(state.entries())))
                        )
                ),
                div(attr("class", "logs-content"),
                        state.empty()
                                ? div(attr("class", "logs-empty"), text("No log entries yet"))
                                : of(state.entries().stream().map(LogsWidget::logRow))
                ),
                script(text(LOGS_CLIENT_SCRIPT))
        );
    }

    @Override
    public void onMounted(final ComponentCompositeKey componentId,
                          final State state,
                          final StateUpdater<State> stateUpdate) {
        subscriptions.computeIfAbsent(componentId, _ ->
                source.subscribe(samples -> stateUpdate.setState(State.from(entries(samples)))));
    }

    @Override
    public void onUnmounted(final ComponentCompositeKey componentId, final State state) {
        Subscription subscription = subscriptions.remove(componentId);
        if (subscription != null) {
            subscription.close();
        }
    }

    private static List<LogEntry> entries(List<TelemetrySample<LogEntry>> samples) {
        return samples.stream().map(TelemetrySample::value).toList();
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
