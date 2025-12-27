package rsp.page;

import rsp.dom.TreePositionPath;
import rsp.page.events.CustomEvent;
import rsp.ref.ElementRef;
import rsp.ref.Ref;
import rsp.util.json.JsonDataType;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An object representing an event's context. Provided as a parameter in an event's handler.
 * This is the main object for an application's code to interact with the framework's internals and access data.
 */
public final class EventContext {
    private final TreePositionPath eventElementPath;
    private final Function<Ref, PropertiesHandle> propertiesHandleLookup;
    private final Function<String, CompletableFuture<JsonDataType>> jsEvaluation;
    private final JsonDataType.Object eventObject;
    private final EventDispatcher eventsDispatcher;
    private final Consumer<String> setHref;

    /**
     * Creates a new instance of an event's context.
     * @param jsEvaluation the proxy function for JavaScript evaluation
     * @param propertiesHandleLookup the proxy function for reading properties values
     * @param eventObject the event's object
     * @param setHref the proxy object for setting browser's URL
     */
    public EventContext(final TreePositionPath eventElementPath,
                        final Function<String, CompletableFuture<JsonDataType>> jsEvaluation,
                        final Function<Ref, PropertiesHandle> propertiesHandleLookup,
                        final JsonDataType.Object eventObject,
                        final EventDispatcher eventsDispatcher,
                        final Consumer<String> setHref) {
        this.eventElementPath = Objects.requireNonNull(eventElementPath);
        this.propertiesHandleLookup = Objects.requireNonNull(propertiesHandleLookup);
        this.jsEvaluation = Objects.requireNonNull(jsEvaluation);
        this.eventObject = Objects.requireNonNull(eventObject);
        this.eventsDispatcher = Objects.requireNonNull(eventsDispatcher);
        this.setHref = Objects.requireNonNull(setHref);
    }

    /**
     * Reads a property value in the client's browser.
     * @param ref a reference to an element
     * @return the proxy object to read the element's properties
     */
    public PropertiesHandle propertiesByRef(final ElementRef ref) {
        Objects.requireNonNull(ref);
        return propertiesHandleLookup.apply(ref);
    }

    /**
     * Evaluates a provided JavaScript expression in the browser returning the evaluation's result.
     * @param js code to execute
     * @return a CompletableFuture of the JSON data type
     */
    public CompletableFuture<JsonDataType> evalJs(final String js) {
        Objects.requireNonNull(js);
        return jsEvaluation.apply(js);
    }

    /**
     * Sets the client browser's URL.
     * @param href URL
     */
    public void setHref(final String href) {
        Objects.requireNonNull(href);
        setHref.accept(href);
    }

    public void dispatchEvent(CustomEvent customEvent) {
        Objects.requireNonNull(customEvent);
        eventsDispatcher.dispatchEvent(eventElementPath, customEvent);
    };

    /**
     * Gets the event's object.
     * @return a Json-like object
     */
    public JsonDataType.Object eventObject() {
        return eventObject;
    }

}
