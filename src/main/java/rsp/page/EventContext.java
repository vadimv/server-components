package rsp.page;

import rsp.ref.ElementRef;
import rsp.ref.Ref;
import rsp.ref.TimerRef;
import rsp.util.json.JsonDataType;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An object representing a event's context. Provided as a parameter in an event's handler.
 * This is the main object for an application's code to interact with RSP internals and access data.
 */
public final class EventContext<S> {
    private final QualifiedSessionId sessionId;
    private final Function<Ref, PropertiesHandle> propertiesHandleLookup;
    private final Function<String, CompletableFuture<JsonDataType>> jsEvaluation;
    private final JsonDataType.Object eventObject;
    private final Schedule<S> executorService;
    private final Consumer<String> setHref;
    private final Consumer<S> stateConsumer;

    /**
     * Creates a new instance of an event's context.
     * @param sessionId page's session Id
     * @param jsEvaluation the proxy function for JavaScript evaluation
     * @param propertiesHandleLookup the proxy function for reading properties values
     * @param eventObject the event's object
     * @param executorService the proxy object for scheduling
     * @param setHref the proxy object for setting browser's URL
     */
    public EventContext(QualifiedSessionId sessionId,
                        Function<String, CompletableFuture<JsonDataType>> jsEvaluation,
                        Function<Ref, PropertiesHandle> propertiesHandleLookup,
                        JsonDataType.Object eventObject,
                        Schedule<S> executorService,
                        Consumer<String> setHref,
                        Consumer<S> stateConsumer) {
        this.sessionId = sessionId;
        this.propertiesHandleLookup = propertiesHandleLookup;
        this.jsEvaluation = jsEvaluation;
        this.eventObject = eventObject;
        this.executorService = executorService;
        this.setHref = setHref;
        this.stateConsumer = stateConsumer;
    }

    /**
     * Reads a property value in the client's browser.
     * @param ref a reference to an element
     * @return the proxy object to read the element's properties
     */
    public PropertiesHandle props(ElementRef ref) {
        return propertiesHandleLookup.apply(ref);
    }

    /**
     * Evaluates a provided JavaScript expression in the browser returning the evaluation's result.
     * @param js code to execute
     * @return a CompletableFuture of the JSON data type
     */
    public CompletableFuture<JsonDataType> evalJs(String js) {
        return jsEvaluation.apply(js);
    }

    /**
     * Sets the client browser's URL.
     * @param href URL
     */
    public void setHref(String href) {
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
    public Timer schedule(Consumer<S> command, int delay, TimeUnit timeUnit) {
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
    public Timer schedule(Consumer<S> command, TimerRef ref, int delay, TimeUnit timeUnit) {
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
    public Timer scheduleAtFixedRate(Consumer<S> command, int delay, int period, TimeUnit timeUnit) {
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
    public Timer scheduleAtFixedRate(Consumer<S> command, TimerRef ref, int delay, int period, TimeUnit timeUnit) {
        return executorService.scheduleAtFixedRate(command, ref, delay, period, timeUnit);
    }

    /**
     * Cancels the schedule previously submitted.
     * If no schedule exists with this name, the cancel command is ignored.
     * @param ref the Id of the schedule to cancel
     */
    public void cancelSchedule(TimerRef ref) {
        executorService.cancel(ref);
    }

    /**
     * Sets new state and initiate re-rendering.
     * @param newState a new state object
     */
    public void setState(S newState) {
        stateConsumer.accept(newState);
    }

    /**
     * Sets new state if the Optional is present and initiate re-rendering.
     * @param newStateOptional an optional state object
     */
    public void setState(Optional<S> newStateOptional) {
        newStateOptional.ifPresent(stateConsumer::accept);
    }

    /**
     * Performs this write operation when the argument {@link CompletableFuture}
     * completes, with its result sets new state and initiate re-rendering.
     * @param newStateCompletableFuture a computation resulting in a write
     */
    public void setState(CompletableFuture<S> newStateCompletableFuture) {
        newStateCompletableFuture.thenAccept(this::setState);
    }
}
