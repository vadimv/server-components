package rsp.stateview;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface NewState<S> {

    void set(S newState);
    void apply(Function<S, S> stateTransformer);
    void applyWhenComplete(CompletableFuture<S> newState);
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
        public void applyWhenComplete(CompletableFuture<T> newState) {
            // NO-OP
        }

        @Override
        public void applyIfPresent(Function<T, Optional<T>> stateTransformer) {
            // NO-OP
        }
    }
}
