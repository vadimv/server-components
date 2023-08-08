package rsp.component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Represents API for initiating a change of a state's snapshot.
 * @param <S> the type of the component's state, should be an immutable class
 */
public interface NewState<S> {

    void set(S newState);
    void apply(Function<S, S> stateTransformer);
    void applyWhenComplete(CompletableFuture<? extends S> newState);
    void applyIfPresent(Function<S, Optional<S>> stateTransformer);


    class Default<T> implements NewState<T> {

          @Override
          public void set(T newState) {
              // NO-OP
          }

          @Override
          public void apply(Function<T, T> stateTransformer) {
              // NO-OP
          }

        @Override
        public void applyWhenComplete(CompletableFuture<? extends T> newState) {
            // NO-OP
        }

        @Override
        public void applyIfPresent(Function<T, Optional<T>> stateTransformer) {
            // NO-OP
        }
    }
}
