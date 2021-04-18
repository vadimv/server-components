package rsp.dsl;

import rsp.Render;

import java.util.function.Consumer;
import java.util.function.Function;

public final class Components {

    public static <S1, S2> Component<S1, S2> component(Render<S2> component, Function<Consumer<S1>, Consumer<S2>> transformation) {
        return new Component.Default<S1, S2>(component, transformation);
    }

}
