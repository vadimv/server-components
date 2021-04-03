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
    public static <S1, S2> ComponentDefinition<S1, S2> component(Render<S2> render, S2 state, Function<S2, S1> stateFun) {
        return new ComponentDefinition<S1, S2>(render, state, (Function<Object, Object>) stateFun);
    }

    /**
     * Creates a component.
     * @param render
     * @param state
     * @param <S>
     * @return
     */
    public static <S> RenderingOnlyComponentDefinition<S> component(Render<S> render, S state) {
        return new RenderingOnlyComponentDefinition<S>(render, state);
    }

}
