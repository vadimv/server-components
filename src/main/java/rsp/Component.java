package rsp;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

@FunctionalInterface
public interface  Component<S> {
    DocumentPartDefinition render(UseState<S> useState);
}
