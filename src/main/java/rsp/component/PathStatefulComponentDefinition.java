package rsp.component;

import rsp.server.Path;

import java.util.function.BiFunction;

public abstract class PathStatefulComponentDefinition<S> extends StatefulComponentDefinition<S> {

    public PathStatefulComponentDefinition(final Object key) {
        super(key);
    }


    @Override
    protected BiFunction<S, Path, Path> state2pathFunction() {
        return (s, p) -> p;
    }
}
