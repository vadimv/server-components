package rsp.component;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Internal capability for updating a component's local state cache.
 * <p>
 * This is intentionally separate from {@link IntentDispatcher}: regular views
 * should receive an intent dispatcher, while component logic and asynchronous
 * lifecycle callbacks receive a state updater.
 *
 * @param <S> immutable component state type
 */
public interface StateUpdater<S> {

    void setState(S newState);

    void applyStateTransformation(UnaryOperator<S> stateTransformer);

    void applyStateTransformationIfPresent(Function<S, Optional<S>> stateTransformer);

    default <T> void publish(EventKey.SimpleKey<T> key, T payload) {
        throw new UnsupportedOperationException("This StateUpdater cannot publish component events");
    }

    default void publish(EventKey.VoidKey key) {
        throw new UnsupportedOperationException("This StateUpdater cannot publish component events");
    }
}
