package rsp.component.definitions;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentComponentTests {

    @Test
    void reducer_handles_typed_intent_without_exposing_a_state_updater_to_the_view() {
        Counter counter = new Counter();
        RecordingStateUpdater updater = new RecordingStateUpdater(4);

        counter.onIntentDispatched(CounterIntent.INCREMENT, 4, updater);

        assertEquals(5, updater.value);
    }

    private enum CounterIntent { INCREMENT, DECREMENT }

    private static final class Counter extends ReducerComponent<Integer, CounterIntent> {
        private Counter() {
            super();
        }

        @Override
        public ComponentStateSupplier<Integer> initStateSupplier() {
            return (_, _) -> 0;
        }

        @Override
        public ComponentView<Integer, CounterIntent> componentView() {
            return _ -> state -> context -> { };
        }

        @Override
        protected Integer reduce(Integer state, CounterIntent intent) {
            return intent == CounterIntent.INCREMENT ? state + 1 : state - 1;
        }
    }

    private static final class RecordingStateUpdater implements StateUpdater<Integer> {
        private Integer value;

        private RecordingStateUpdater(Integer value) {
            this.value = value;
        }

        @Override
        public void setState(Integer newState) {
            value = newState;
        }

        @Override
        public void applyStateTransformation(UnaryOperator<Integer> stateTransformer) {
            value = stateTransformer.apply(value);
        }

        @Override
        public void applyStateTransformationIfPresent(Function<Integer, Optional<Integer>> stateTransformer) {
            stateTransformer.apply(value).ifPresent(next -> value = next);
        }
    }
}
