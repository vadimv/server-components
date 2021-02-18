package rsp.page;

import rsp.dsl.Ref;
import rsp.util.json.JsonDataType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public final class EventContext {
    private final QualifiedSessionId sessionId;
    private final Function<Ref, PropertiesHandle> propertiesHandleLookup;
    private final Function<String, CompletableFuture<JsonDataType>> jsEvaluation;
    private final JsonDataType.Object eventObject;
    private final Schedule executorService;
    private final Consumer<String> setHref;

    public EventContext(QualifiedSessionId sessionId,
                        Function<String, CompletableFuture<JsonDataType>> jsEvaluation,
                        Function<Ref, PropertiesHandle> propertiesHandleLookup,
                        JsonDataType.Object eventObject,
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
     * Evaluates a JavaScript expression in the browser returning a result
     * @param js code to execute
     * @return a CompletableFuture of the JSON data type
     */
    public CompletableFuture<JsonDataType> evalJs(String js) {
        return jsEvaluation.apply(js);
    }

    public void setHref(String href) {
        setHref.accept(href);
    }

    public QualifiedSessionId sessionId() {
        return sessionId;
    }

    public JsonDataType.Object eventObject() {
        return eventObject;
    }

    public ScheduledFuture<?> schedule(Runnable command, int delay, TimeUnit timeUnit) {
        return executorService.schedule(command, new Object(), delay, timeUnit);
    }

    public ScheduledFuture<?> schedule(Runnable command, String name, int delay, TimeUnit timeUnit) {
        return executorService.schedule(command, name, delay, timeUnit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, int delay, int period, TimeUnit timeUnit) {
        return executorService.scheduleAtFixedRate(command, new Object(), delay, period, timeUnit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, String name, int delay, int period, TimeUnit timeUnit) {
        return executorService.scheduleAtFixedRate(command, name, delay, period, timeUnit);
    }

    public void cancelSchedule(String name) {
        executorService.cancel(name);
    }
}
