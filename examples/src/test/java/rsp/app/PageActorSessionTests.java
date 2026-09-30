package rsp.app;

import org.junit.jupiter.api.Test;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorEffect;
import rsp.actor.ActorType;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;
import rsp.actor.testkit.ManualActorScheduler;
import rsp.actor.ui.ActorComponent;
import rsp.actor.ui.ActorComponentContext;
import rsp.actor.ui.PageActorDirectory;
import rsp.actor.ui.PageActorPlacement;
import rsp.actor.ui.PageActorRuntime;
import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.http.PageApplication;
import rsp.http.WebServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.dsl.Html.*;

class PageActorSessionTests {
    @Test
    void queuedBehaviorAsyncCompletionAndTimerRunWithoutWebSocketAttachment() throws Exception {
        var type = ActorType.named("unattached-page", String.class, (Long id) -> id.toString());
        var scheduler = new ManualActorScheduler();
        var runtime = PageActorRuntime.builder().scheduler(scheduler).build();
        var directory = PageActorDirectory.numbered(runtime, type);
        var behaviorStarted = new CompletableFuture<Void>();
        var asyncResult = new CompletableFuture<Integer>();
        var tickState = new CompletableFuture<Integer>();
        var receipt = new AtomicReference<ProcessingReceipt>();
        var definition = ActorDefinition.<Integer, String>builder(type)
                .initialState(_ -> 0)
                .behavior((context, state, message) -> {
                    if (message.equals("async")) {
                        behaviorStarted.complete(null);
                        return asyncResult.thenApply(value -> ActorEffect.state(value)
                                .schedule(context.self(), "tick", Duration.ofSeconds(1)));
                    }
                    tickState.complete(state);
                    return CompletableFuture.completedFuture(ActorEffect.state(state + 1));
                }).build();
        var component = new ActorComponent<Integer, String>() {
            protected ActorDefinition<Integer, String> definition() { return definition; }
            protected PageActorPlacement<String> placement(ActorComponentContext context) {
                return context.in(directory);
            }
            public ComponentView<Integer, String> componentView() {
                return _ -> state -> html(head(title("async actor")), body(p("state " + state)));
            }
            public void onMounted(ComponentCompositeKey id, Integer state, StateUpdater<Integer> updater) {
                receipt.set(directory.find(1L).orElseThrow().ref().track("async"));
                assertFalse(behaviorStarted.isDone(), "turns must wait until rendering has finished");
            }
        };
        try (var server = new WebServer(0)
                .pageApplication(PageApplication.withLifecycle(runtime,
                        _ -> rsp.http.HttpResponse.status(rsp.http.HttpStatus.NOT_FOUND).build()))
                .page("/", (_, _) -> component);
             var client = HttpClient.newHttpClient()) {
            server.start();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port()))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("state 0"));
            behaviorStarted.get(2, TimeUnit.SECONDS);
            assertFalse(receipt.get().processed().toCompletableFuture().isDone());

            asyncResult.complete(7);
            receipt.get().processed().toCompletableFuture().get(2, TimeUnit.SECONDS);
            scheduler.advance(Duration.ofSeconds(1));
            assertEquals(7, tickState.get(2, TimeUnit.SECONDS));

            var ref = directory.find(1L).orElseThrow().ref();
            server.stop();
            assertEquals(SendResult.STOPPED, ref.tell("late"));
            assertTrue(directory.all().isEmpty());
            assertEquals(0, scheduler.pendingCount());
        }
    }
}
