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

public class CountersView implements ComponentView<Integer> {

    private final String name;

    public CountersView(String name) {
        this.name = name;
    }

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
