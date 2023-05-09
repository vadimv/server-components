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
public interface UseStateComponentFunction<S> {

    /**
     * Constructs a UI definition from the state provided.
     * In this method the component's state is reflected to its UI presentation and events handlers registered.
     * @param us a read and write state accessor object
     * @return the result component's definition
     */
    DocumentPartDefinition apply(UseState<S> us);

    default DocumentPartDefinition apply(S s) {
        return apply(UseState.readOnly(() -> s));
    }

    default DocumentPartDefinition apply(S s, Consumer<S> c) {
        return apply(UseState.readWrite(() -> s, c));
    }

    default DocumentPartDefinition applyCompletableFuture(S s, CompletableFutureConsumer<S> c) {
        return apply(UseState.readWriteInCompletableFuture(() -> s, c));
    }

    default DocumentPartDefinition apply(S s, FunctionConsumer<S> c) {
        return apply(UseState.readWriteInFunction(() -> s, c));
    }
}
