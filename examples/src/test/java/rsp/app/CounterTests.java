package rsp.app;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.metrics.RecordingMetrics;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTests {

    @Test
    void increment_metric_is_cumulative_across_page_components() {
        final RecordingMetrics metrics = new RecordingMetrics();
        final ComponentView<Integer, Counter.CounterIntent> view = _ -> _ -> _ -> { };
        final Counter.CounterComponent firstPage = new Counter.CounterComponent(view, metrics);
        final Counter.CounterComponent secondPage = new Counter.CounterComponent(view, metrics);

        final RecordingStateUpdater firstState = new RecordingStateUpdater(0);
        firstPage.onIntentDispatched(Counter.CounterIntent.INCREMENT, 0, firstState);
        firstPage.onIntentDispatched(Counter.CounterIntent.INCREMENT, 0, firstState);

        final RecordingStateUpdater secondState = new RecordingStateUpdater(0);
        secondPage.onIntentDispatched(Counter.CounterIntent.INCREMENT, 0, secondState);

        assertEquals(2, firstState.value);
        assertEquals(1, secondState.value);
        assertEquals(3, metrics.counter(Counter.INCREMENTS.name()));
    }

    private static final class RecordingStateUpdater implements StateUpdater<Integer> {
        private int value;

        private RecordingStateUpdater(final int value) {
            this.value = value;
        }

        @Override
        public void setState(final Integer newState) {
            value = newState;
        }

        @Override
        public void applyStateTransformation(final UnaryOperator<Integer> stateTransformer) {
            value = stateTransformer.apply(value);
        }

        @Override
        public void applyStateTransformationIfPresent(
                final Function<Integer, Optional<Integer>> stateTransformer) {
            stateTransformer.apply(value).ifPresent(next -> value = next);
        }
    }
}
