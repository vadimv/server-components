package rsp.actor.ui;

import org.junit.jupiter.api.Test;
import rsp.actor.SendResult;
import rsp.component.StateUpdater;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class UiActorSinkTests {
    @Test
    void coalescesFullSnapshotsToLatestPendingValue() {
        QueuedUpdater updater = new QueuedUpdater();
        UiActorSink<Integer, Integer> sink = UiActorSink.latest(updater, (_, event) -> event);
        assertEquals(SendResult.ACCEPTED, sink.tell(1));
        assertEquals(SendResult.ACCEPTED, sink.tell(2));
        assertEquals(SendResult.ACCEPTED, sink.tell(3));
        assertEquals(1, updater.pending());
        updater.runAll();
        assertEquals(3, updater.state);
        sink.close();
    }

    @Test
    void closeDropsQueuedAndLateSnapshots() {
        QueuedUpdater updater = new QueuedUpdater();
        UiActorSink<Integer, Integer> sink = UiActorSink.latest(updater, (_, event) -> event);
        assertEquals(SendResult.ACCEPTED, sink.tell(5));
        sink.close();
        assertEquals(SendResult.STOPPED, sink.tell(6));
        assertEquals(SendResult.STOPPED, sink.track(7).admission());
        updater.runAll();
        assertEquals(0, updater.state);
    }

    @Test
    void rejectedPageQueueDoesNotFailTheSendingActor() {
        StateUpdater<Integer> closedPage = new StateUpdater<>() {
            @Override
            public void setState(Integer next) {
                throw new IllegalStateException("page closed");
            }

            @Override
            public void applyStateTransformation(UnaryOperator<Integer> transformation) {
                throw new IllegalStateException("page closed");
            }

            @Override
            public void applyStateTransformationIfPresent(Function<Integer, Optional<Integer>> transformation) {
                throw new IllegalStateException("page closed");
            }
        };
        UiActorSink<Integer, Integer> sink = UiActorSink.latest(closedPage, (_, event) -> event);
        assertEquals(SendResult.STOPPED, sink.tell(1));
        assertEquals(SendResult.STOPPED, sink.tell(2));
    }

    private static final class QueuedUpdater implements StateUpdater<Integer> {
        private final Queue<UnaryOperator<Integer>> work = new ArrayDeque<>();
        private int state;

        @Override
        public void setState(Integer next) {
            work.add(_ -> next);
        }

        @Override
        public void applyStateTransformation(UnaryOperator<Integer> transformation) {
            work.add(transformation);
        }

        @Override
        public void applyStateTransformationIfPresent(Function<Integer, Optional<Integer>> transformation) {
            work.add(current -> transformation.apply(current).orElse(current));
        }

        int pending() {
            return work.size();
        }

        void runAll() {
            while (!work.isEmpty()) {
                state = work.remove().apply(state);
            }
        }
    }
}
