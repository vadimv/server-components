package rsp.app.counters;

import rsp.component.ComponentView;
import rsp.component.StateUpdate;
import rsp.component.View;
import rsp.page.EventContext;

import java.util.function.Consumer;

import static rsp.dsl.Html.*;
import static rsp.dsl.Html.attr;
import static rsp.dsl.Html.button;
import static rsp.dsl.Html.on;
import static rsp.dsl.Html.span;
import static rsp.dsl.Html.text;

/**
 * A reusable counter view implementation demonstrating a decoupled implementation of a view with events handlers logic.
 * <p>
 * <strong>Reusability:</strong> This view is shared across multiple component types:
 * <ul>
 *   <li>{@link ContextCounterComponent} - URL path/query synced counters</li>
 *   <li>{@link CachedCounterComponent} - persistent state counters</li>
 * </ul>
 * This demonstrates how a single view implementation can be composed with different state management strategies.
 *
 * @see ComponentView for the base interface for views
 * @see View for the state-to-markup transformation
 * @see ContextCounterComponent for URL-synced counter example
 * @see CachedCounterComponent for persistent state counter example
 */
    public class CountersView implements ComponentView<Integer> {

    private final String name;

    /**
     * Creates a counter view for the given counter name.
     * @param name the counter identifier (used as label and HTML element ID prefix)
     */
    public CountersView(String name) {
        this.name = name;
    }

    /**
     * Applies the state update API to create a stateful view function.
     * <p>
     * This implements the outer level of the two-level composition pattern.
     * The returned View function will be called repeatedly as the state changes.
     * <p>
     * <strong>Closure capture:</strong> The {@code newState} parameter is captured in the returned
     * View's closures, allowing event handlers to access the state update API.
     *
     * @param newState the state update API - used by event handlers to dispatch state changes
     * @return a View function that transforms the current state into DOM markup
     */
    @Override
    public View<Integer> use(StateUpdate<Integer> newState) {
        return state ->
                div(span(name),
                        button(attr("type", "button"),
                                attr("id", name + "_b0"),
                                text("+"),
                                on("click",
                                        counterButtonClickHandlerPlus(state, newState))),
                        span(attr("id", name + "_s0"),
                                attr("class", state % 2 == 0 ? "red" : "blue"),
                                text(state)),
                        button(attr("type", "button"),
                                attr("id", name + "_b1"),
                                text("-"),
                                on("click",
                                        counterButtonClickHandlerMinus(state, newState))));
    }

    private static Consumer<EventContext> counterButtonClickHandlerPlus(Integer state, StateUpdate<Integer> newState) {
        return  _ -> newState.setState(state + 1);
    }

    private static Consumer<EventContext> counterButtonClickHandlerMinus(Integer state, StateUpdate<Integer> newState) {
        return  _ -> newState.setState(state - 1);
    }

}
