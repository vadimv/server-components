package rsp;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

@FunctionalInterface
public interface  Component<S> {
    DocumentPartDefinition materialize(UseState<S> useState);
}
