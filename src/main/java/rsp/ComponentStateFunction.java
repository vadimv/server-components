package rsp;

import rsp.html.DocumentPartDefinition;
import rsp.state.CompletableFutureConsumer;
import rsp.state.FunctionConsumer;
import rsp.state.UseState;

import java.util.function.Consumer;

/**
 * A state's view representation.
 * @param <S> the type of the document part's related state, should be an immutable class
 */
@FunctionalInterface
public interface ComponentStateFunction<S> {

    /**
     * Constructs a UI definition from the state provided.
     * In this method the component's state is reflected to its UI presentation and events handlers registered.
     * @param sv the current state object
     * @param sc the state Consumer object
     * @return the result component's definition
     */
    DocumentPartDefinition apply(S sv, Consumer<S> sc);

    /*default DocumentPartDefinition apply(S s) {
        return apply(s, v -> {});
    }*/

}
