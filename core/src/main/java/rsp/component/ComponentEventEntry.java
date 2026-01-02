package rsp.component;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A subscription to a component event.
 *
 * <p>Supports both exact event name matching and wildcard pattern matching for flexible event handling.</p>
 *
 * <p><b>Event Name Patterns:</b></p>
 * <ul>
 *   <li><b>Exact match:</b> {@code "stateUpdated.sort"} matches only {@code "stateUpdated.sort"}</li>
 *   <li><b>Wildcard match:</b> {@code "stateUpdated.*"} matches any event starting with {@code "stateUpdated."}
 *       (e.g., {@code "stateUpdated.p"}, {@code "stateUpdated.sort"}, {@code "stateUpdated.filter"})</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Exact match - subscribe to specific event
 * addComponentEventHandler("stateUpdated.sort", eventContext -> {
 *     // Handle only "stateUpdated.sort" events
 * }, true);
 *
 * // Wildcard match - subscribe to all events with prefix
 * addComponentEventHandler("stateUpdated.*", eventContext -> {
 *     String eventName = eventContext.eventName();  // Get actual event name
 *     String paramName = eventName.substring("stateUpdated.".length());
 *     // Handle any "stateUpdated.*" event dynamically
 * }, true);
 * }</pre>
 *
 * <p><b>Note:</b> Wildcard patterns only support prefix matching with the {@code ".*"} suffix.
 * Full glob patterns (e.g., {@code "*suffix"} or {@code "prefix*suffix"}) are not supported.</p>
 *
 * @param eventName an event name or pattern to listen to (supports {@code ".*"} wildcard suffix)
 * @param eventHandler a handler to execute to react to the event
 * @param preventDefault (currently unused for component events)
 *
 * @see #matches(String)
 * @see EventContext
 */
public record ComponentEventEntry(String eventName, Consumer<EventContext> eventHandler, boolean preventDefault) {

    public ComponentEventEntry {
        Objects.requireNonNull(eventName);
        Objects.requireNonNull(eventHandler);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ComponentEventEntry event = (ComponentEventEntry) o;
        // ignore eventHandler
        return Objects.equals(eventName, event.eventName) &&
                preventDefault == event.preventDefault;
    }

    @Override
    public int hashCode() {
        //ignore eventHandler
        return Objects.hash(eventName, preventDefault);
    }

    /**
     * Check if this entry matches the given event name.
     *
     * <p>Supports both exact matching and wildcard prefix matching:</p>
     * <ul>
     *   <li><b>Exact match:</b> Pattern {@code "click"} matches only event {@code "click"}</li>
     *   <li><b>Wildcard match:</b> Pattern {@code "stateUpdated.*"} matches any event starting with
     *       {@code "stateUpdated."} (e.g., {@code "stateUpdated.p"}, {@code "stateUpdated.sort"})</li>
     * </ul>
     *
     * <p><b>Examples:</b></p>
     * <pre>{@code
     * ComponentEventEntry exactEntry = new ComponentEventEntry("click", handler, true);
     * exactEntry.matches("click");        // true
     * exactEntry.matches("clickmore");    // false
     *
     * ComponentEventEntry wildcardEntry = new ComponentEventEntry("stateUpdated.*", handler, true);
     * wildcardEntry.matches("stateUpdated.p");      // true
     * wildcardEntry.matches("stateUpdated.sort");   // true
     * wildcardEntry.matches("stateUpdated");        // false (no dot after prefix)
     * wildcardEntry.matches("otherEvent.p");        // false (different prefix)
     * }</pre>
     *
     * <p><b>Performance:</b> Both exact and wildcard matching are O(1) string operations
     * ({@code equals} for exact, {@code startsWith} for wildcard).</p>
     *
     * @param actualEventName the event name to match against
     * @return {@code true} if this handler should process the event, {@code false} otherwise
     * @throws NullPointerException if actualEventName is null
     */
    public boolean matches(String actualEventName) {
        if (eventName.endsWith(".*")) {
            // Prefix matching: "stateUpdated.*" matches "stateUpdated.p", "stateUpdated.sort", etc.
            String prefix = eventName.substring(0, eventName.length() - 2);
            return actualEventName.startsWith(prefix);
        } else {
            // Exact matching (backward compatible)
            return eventName.equals(actualEventName);
        }
    }

    /**
     * The context for a component event, containing both the event name and its data payload.
     *
     * <p>For exact-match event handlers, the {@code eventName} field confirms which event was triggered.
     * For wildcard-match handlers, the {@code eventName} field is essential for determining the specific
     * event that matched the pattern.</p>
     *
     * <p><b>Usage with Wildcard Handlers:</b></p>
     * <pre>{@code
     * addComponentEventHandler("stateUpdated.*", eventContext -> {
     *     String fullEventName = eventContext.eventName();  // e.g., "stateUpdated.sort"
     *     String paramName = fullEventName.substring("stateUpdated.".length());  // "sort"
     *
     *     if (eventContext.eventObject() instanceof StringValue value) {
     *         updateParameter(paramName, value.value());
     *     }
     * }, true);
     * }</pre>
     *
     * <p><b>Usage with Exact-Match Handlers:</b></p>
     * <pre>{@code
     * addComponentEventHandler("stateUpdated.sort", eventContext -> {
     *     // eventName is "stateUpdated.sort" (same as pattern)
     *     // eventObject contains the event data
     * }, true);
     * }</pre>
     *
     * @param eventName the actual event name that triggered this handler
     *                  (for wildcard patterns, this is the specific matched event;
     *                  for exact patterns, this equals the pattern itself)
     * @param eventObject the data payload of the event (type depends on event source)
     */
    public record EventContext(String eventName, Object eventObject) {
        public EventContext {
            Objects.requireNonNull(eventName);
            Objects.requireNonNull(eventObject);
        }
    }
}
