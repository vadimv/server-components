package rsp.page;

import rsp.ref.ElementRef;
import rsp.ref.Ref;
import rsp.ref.TimerRef;
import rsp.util.json.JsonDataType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An object representing a event's context. Provided as a parameter in an event's handler.
 * This is the main object for an application's code to interact with RSP internals and access data.
 */
public final class EventContext {
    private final QualifiedSessionId sessionId;
    private final Function<Ref, PropertiesHandle> propertiesHandleLookup;
    private final Function<String, CompletableFuture<JsonDataType>> jsEvaluation;
    private final JsonDataType.Object eventObject;
    private final Schedule executorService;
    private final Consumer<String> setHref;

    /**
     * Creates a new instance of an event's context.
     * @param sessionId page's session Id
     * @param jsEvaluation the proxy function for JavaScript evaluation
     * @param propertiesHandleLookup the proxy function for reading properties values
     * @param eventObject the event's object
     * @param executorService the proxy object for scheduling
     * @param setHref the proxy object for setting browser's URL
     */
    public EventContext(final QualifiedSessionId sessionId,
                        final Function<String, CompletableFuture<JsonDataType>> jsEvaluation,
                        final Function<Ref, PropertiesHandle> propertiesHandleLookup,
                        final JsonDataType.Object eventObject,
                        final Schedule executorService,
                        final Consumer<String> setHref) {
        this.sessionId = sessionId;
        this.propertiesHandleLookup = propertiesHandleLookup;
        this.jsEvaluation = jsEvaluation;
        this.eventObject = eventObject;
        this.executorService = executorService;
        this.setHref = setHref;
    }

    /**
     * Reads a property value in the client's browser.
     * @param ref a reference to an element
     * @return the proxy object to read the element's properties
     */
    public PropertiesHandle props(final ElementRef ref) {
        return propertiesHandleLookup.apply(ref);
    }

    /**
     * Evaluates a provided JavaScript expression in the browser returning the evaluation's result.
     * @param js code to execute
     * @return a CompletableFuture of the JSON data type
     */
    public CompletableFuture<JsonDataType> evalJs(final String js) {
        return jsEvaluation.apply(js);
    }

    /**
     * Sets the client browser's URL.
     * @param href URL
     */
    public void setHref(final String href) {
        setHref.accept(href);
    }

    /**
     * Gets the current page session Id.
     * @return Id
     */
    public QualifiedSessionId sessionId() {
        return sessionId;
    }

    /**
     * Gets the event's object.
     * @return a Json-like object
     */
    public JsonDataType.Object eventObject() {
        return eventObject;
    }

    /**
     * Submits a one-shot task that becomes enabled after the given delay.
     * @param command the task to execute
     * @param delay the time from now to delay execution
     * @param timeUnit the time unit of the delay parameter
     * @return a timer representing pending completion of the delayed task
     */
    public Timer schedule(final Runnable command, final int delay, final TimeUnit timeUnit) {
        return executorService.schedule(command, new Object(), delay, timeUnit);
    }

    /**
     * Submits a one-shot task that becomes enabled after the given delay.
     * @param command the task to execute
     * @param ref the timer's Id
     * @param delay the time from now to delay execution
     * @param timeUnit the time unit of the delay parameter
     * @return a timer representing pending completion of the delayed task
     */
    public Timer schedule(final Runnable command, final TimerRef ref, final int delay, final TimeUnit timeUnit) {
        return executorService.schedule(command, ref, delay, timeUnit);
    }

    /**
     * Submits a periodic action that becomes enabled first after the
     * given initial delay, and subsequently with the given period.
     * @param command the task to execute
     * @param delay the time from now to delay execution
     * @param period the period between successive executions
     * @param timeUnit the time unit of the delay parameter
     * @return a timer representing pending completion of
     *         the series of repeated tasks
     */
    public Timer scheduleAtFixedRate(final Runnable command, final int delay, final int period, final TimeUnit timeUnit) {
        return executorService.scheduleAtFixedRate(command, new Object(), delay, period, timeUnit);
    }

    /**
     * Submits a periodic action that becomes enabled first after the
     * given initial delay, and subsequently with the given period.
     * @param command the task to execute
     * @param ref the timer's Id
     * @param delay the time from now to delay execution
     * @param period the period between successive executions
     * @param timeUnit the time unit of the delay parameter
     * @return a timer representing pending completion of
     *         the series of repeated tasks
     */
    public Timer scheduleAtFixedRate(final Runnable command, final TimerRef ref, final int delay, final int period, final TimeUnit timeUnit) {
        return executorService.scheduleAtFixedRate(command, ref, delay, period, timeUnit);
    }

    /**
     * Cancels the schedule previously submitted.
     * If no schedule exists with this name, the cancel command is ignored.
     * @param ref the Id of the schedule to cancel
     */
    public void cancelSchedule(final TimerRef ref) {
        executorService.cancel(ref);
    }
}
