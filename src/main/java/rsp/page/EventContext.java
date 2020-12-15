package rsp.page;

import rsp.dsl.Ref;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public final class EventContext {
    private final QualifiedSessionId sessionId;
    private final Function<Ref, PropertiesHandle> propertiesHandleLookup;
    private final Function<String, CompletableFuture<Object>> jsEvaluation;
    private final Function<String, Optional<String>> eventObject;
    private final Schedule executorService;
    private final Consumer<String> setHref;

    public EventContext(QualifiedSessionId sessionId,
                        Function<String, CompletableFuture<Object>> jsEvaluation,
                        Function<Ref, PropertiesHandle> propertiesHandleLookup,
                        Function<String, Optional<String>> eventObject,
                        Schedule executorService,
                        Consumer<String> setHref) {
        this.sessionId = sessionId;
        this.propertiesHandleLookup = propertiesHandleLookup;
        this.jsEvaluation = jsEvaluation;
        this.eventObject = eventObject;
        this.executorService = executorService;
        this.setHref = setHref;
    }

    public PropertiesHandle props(Ref ref) {
        return propertiesHandleLookup.apply(ref);
    }

    /**
     * Evaluates a JavaScript expression on the browser returning a result
     * @param js code to execute
     * @return a CompletableFuture of either a Map, String, Long, Double or Boolean according to its evaluation result JSON data type
     */
    public CompletableFuture<Object> evalJs(String js) {
        return jsEvaluation.apply(js);
    }

    public void setHref(String href) {
        setHref.accept(href);
    }

    public QualifiedSessionId sessionId() {
        return sessionId;
    }

    // TODO maybe replace with an Object/Map
    public Function<String, Optional<String>> eventObject() {
        return eventObject;
    }

    public ScheduledFuture<?> schedule(Runnable command, int delay, TimeUnit timeUnit) {
        return executorService.schedule(command, delay, timeUnit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, int delay, int period, TimeUnit timeUnit) {
        return executorService.scheduleAtFixedRate(command, delay, period, timeUnit);
    }
}
