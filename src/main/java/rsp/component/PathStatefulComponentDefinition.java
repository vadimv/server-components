package rsp.component;

import rsp.server.Path;

import java.util.function.BiFunction;

public abstract class PathStatefulComponentDefinition<S> extends StatefulComponentDefinition<Path, S> {

    @Override
    protected Class<Path> stateFunctionInputClass() {
        return Path.class;
    }

    @Override
    protected BiFunction<S, Path, Path> state2pathFunction() {
        return (s, p) -> p;
    }
}
