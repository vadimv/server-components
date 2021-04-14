package rsp.dsl;

import rsp.Render;

import java.util.function.Function;

public final class Components {
    /**
     * Creates a component.
     * @param render
     * @param state
     * @param stateFun
     * @param <S1>
     * @param <S2>
     * @return
     */
/*    public static <S1, S2> ComponentDefinition<S1, S2> renderComponent(Render<S2> render, S2 state, Function<S2, S1> stateFun) {
        return new ComponentDefinition.Default<S1, S2>(render, state, stateFun);
    }*/

    /**
     * Creates a component.
     * @param render
     * @param state
     * @param <S>
     * @return
     */
    public static <S> RenderOnlyComponentDefinition<S> renderComponent(Render<S> render, S state) {
        return new RenderOnlyComponentDefinition.Default<S>(render, state);
    }

}
