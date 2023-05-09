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
public interface StateView<S> {
    /**
     * Constructs a UI definition from the state provided.
     * In this method the component's state is reflected to its UI presentation and events handlers registered.
     * @param us a read and write state accessor object
     * @return the result component's definition
     */
    DocumentPartDefinition render(UseState<S> us);

    default DocumentPartDefinition render(S s) {
        return render(UseState.readOnly(() -> s));
    }

    default DocumentPartDefinition render(S s, Consumer<S> c) {
        return render(UseState.readWrite(() -> s, c));
    }

    default DocumentPartDefinition renderWithCompletableFuture(S s, CompletableFutureConsumer<S> c) {
        return render(UseState.readWriteInCompletableFuture(() -> s, c));
    }

    default DocumentPartDefinition renderWithFunction(S s, FunctionConsumer<S> c) {
        return render(UseState.readWriteInFunction(() -> s, c));
    }
}
