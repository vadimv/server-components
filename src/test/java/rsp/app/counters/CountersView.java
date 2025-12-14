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
 * A reusable counter view implementation demonstrating the @FunctionalInterface pattern.
 * <p>
 * This class implements {@link ComponentView}&lt;Integer&gt;, which is a functional interface:
 * <ul>
 *   <li><strong>Input:</strong> {@link StateUpdate}&lt;Integer&gt; - provides setState() to dispatch state changes</li>
 *   <li><strong>Output:</strong> {@link View}&lt;Integer&gt; - a function that takes current state and returns DOM markup</li>
 * </ul>
 * <p>
 * The two-level composition pattern (ComponentView → View) enables:
 * <ul>
 *   <li><strong>State update capability:</strong> The outer function (ComponentView) receives the state update API</li>
 *   <li><strong>State-driven rendering:</strong> The inner function (View) receives the actual state value</li>
 *   <li><strong>Event handling:</strong> Event handlers captured in closures access both the current state and update API</li>
 * </ul>
 * <p>
 * <strong>Rendering flow:</strong>
 * <pre>
 * ComponentView.apply(stateUpdater)     // Called once per component mount
 *   ↓
 * returns View&lt;Integer&gt;
 *   ↓
 * View.apply(currentState)             // Called on each state change and initial render
 *   ↓
 * returns Definition (DOM markup)
 * </pre>
 * <p>
 * <strong>UI Structure:</strong> Renders a counter with:
 * <ul>
 *   <li>Label with counter name (e.g., \"c1\")</li>
 *   <li>Increment (+) button - increments state by 1</li>
 *   <li>Value display - shows current state with CSS class based on even/odd</li>
 *   <li>Decrement (-) button - decrements state by 1</li>
 * </ul>
 * <p>
 * <strong>Reusability:</strong> This view is shared across multiple component types:
 * <ul>
 *   <li>{@link ContextCounterComponent} - URL path/query synced counters</li>
 *   <li>{@link CachedCounterComponent} - persistent state counters</li>
 * </ul>
 * This demonstrates how a single view implementation can be composed with different state management strategies.
 * <p>
 * <strong>Example usage:</strong>
 * <pre>
 * // In ContextCounterComponent
 * @Override
 * public ComponentView&lt;Integer&gt; componentView() {
 *     return new CountersView(\"c1\");  // Reuses this view for counter c1
 * }
 * </pre>
 *
 * @see ComponentView for the functional interface contract
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
    public View<Integer> apply(StateUpdate<Integer> newState) {
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
