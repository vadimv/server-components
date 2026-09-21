package rsp.actor.ui;

import org.junit.jupiter.api.Test;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorRef;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;
import rsp.component.StateUpdater;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.PageBuilder;
import rsp.page.QualifiedSessionId;
import rsp.page.RedirectableEventsConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class UiActorBindingTests {
    @Test
    void facadeOwnsAcceptedBindingOnTheMountedSegment() {
        List<String> messages = new ArrayList<>();
        AtomicReference<ActorRef<Integer>> subscriber = new AtomicReference<>();
        ComponentSegment<Integer> segment = unrenderedSegment();
        List<UnaryOperator<Integer>> pending = new ArrayList<>();

        SendResult result = UiActors.<Integer, Integer, String>observe(segment, queuedUpdater(pending),
                actor(messages, SendResult.ACCEPTED), (_, snapshot) -> snapshot,
                sink -> { subscriber.set(sink); return "subscribe"; },
                _ -> "unsubscribe");

        assertEquals(SendResult.ACCEPTED, result);
        assertEquals(SendResult.ACCEPTED, subscriber.get().tell(9));
        segment.unmount();
        assertEquals(List.of("subscribe", "unsubscribe"), messages);
        assertEquals(SendResult.STOPPED, subscriber.get().tell(10));
        assertEquals(0, pending.getFirst().apply(0));
    }

    @Test
    void subscriptionIsMountOwnedAndLateSnapshotsAreDropped() {
        List<String> messages = new ArrayList<>();
        AtomicReference<ActorRef<Integer>> subscriber = new AtomicReference<>();
        ActorRef<String> actor = actor(messages, SendResult.ACCEPTED);
        List<UnaryOperator<Integer>> pending = new ArrayList<>();
        StateUpdater<Integer> updater = queuedUpdater(pending);

        UiActorBinding<Integer, Integer, String> binding = UiActorBinding.subscribe(actor, updater,
                (_, snapshot) -> snapshot,
                sink -> { subscriber.set(sink); return "subscribe"; },
                sink -> { assertSame(subscriber.get(), sink); return "unsubscribe"; });
        assertEquals(SendResult.ACCEPTED, binding.admission());
        assertEquals(List.of("subscribe"), messages);
        assertEquals(SendResult.ACCEPTED, subscriber.get().tell(7));
        assertEquals(1, pending.size());

        binding.close();
        binding.close();
        assertEquals(List.of("subscribe", "unsubscribe"), messages);
        assertEquals(SendResult.STOPPED, subscriber.get().tell(8));
        assertEquals(0, pending.getFirst().apply(0));
        assertEquals(SendResult.STOPPED, binding.tell("command"));
    }

    @Test
    void rejectedSubscriptionClosesSinkWithoutUnsubscribing() {
        List<String> messages = new ArrayList<>();
        UiActorBinding<Integer, Integer, String> binding = UiActorBinding.subscribe(
                actor(messages, SendResult.MAILBOX_FULL), queuedUpdater(new ArrayList<>()),
                (_, snapshot) -> snapshot, _ -> "subscribe", _ -> "unsubscribe");
        assertEquals(SendResult.MAILBOX_FULL, binding.admission());
        binding.close();
        assertEquals(List.of("subscribe"), messages);
    }

    private static ActorRef<String> actor(List<String> messages, SendResult result) {
        return new ActorRef<>() {
            @Override
            public SendResult tell(ActorEnvelope<String> envelope) {
                messages.add(envelope.message());
                return result;
            }

            @Override
            public ProcessingReceipt track(ActorEnvelope<String> envelope) {
                return new ProcessingReceipt(tell(envelope), CompletableFuture.completedFuture(null));
            }
        };
    }

    private static ComponentSegment<Integer> unrenderedSegment() {
        QualifiedSessionId id = new QualifiedSessionId("device", "page");
        RedirectableEventsConsumer commands = new RedirectableEventsConsumer();
        PageBuilder builder = new PageBuilder(id, Optional.empty(), new ComponentContext(), commands);
        Component<Integer, String> component = new Component<>() {
            @Override
            public ComponentStateSupplier<Integer> initStateSupplier() {
                return (_, _) -> 0;
            }

            @Override
            public ComponentView<Integer, String> componentView() {
                return _ -> _ -> rsp.dsl.Html.div();
            }
        };
        return component.createComponentSegment(id, TreePositionPath.of("1"),
                builder, new ComponentContext(), commands);
    }

    private static StateUpdater<Integer> queuedUpdater(List<UnaryOperator<Integer>> pending) {
        return new StateUpdater<>() {
            @Override
            public void setState(Integer state) {
                pending.add(_ -> state);
            }

            @Override
            public void applyStateTransformation(UnaryOperator<Integer> transformation) {
                pending.add(transformation);
            }

            @Override
            public void applyStateTransformationIfPresent(Function<Integer, Optional<Integer>> transformation) {
                pending.add(current -> transformation.apply(current).orElse(current));
            }
        };
    }
}
