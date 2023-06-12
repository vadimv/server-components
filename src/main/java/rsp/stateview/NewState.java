package rsp.stateview;

import java.util.function.Function;

public interface NewState<S> {

    class Default<T> implements NewState<T> {

          @Override
          public void set(T newState) {
              // NO-OP
          }

          @Override
          public void apply(Function<T, T> stateTransformer) {
              // NO-OP
          }
    }

    void set(S newState);
    void apply(Function<S, S> stateTransformer);
}
