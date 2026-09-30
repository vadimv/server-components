package rsp.app;

import org.junit.jupiter.api.Test;
import rsp.actor.ActorBehavior;
import rsp.actor.ActorDefinition;
import rsp.actor.ActorEffect;
import rsp.actor.ActorType;
import rsp.actor.runtime.LocalActorSystem;
import rsp.actor.ui.ActorBinding;
import rsp.actor.ui.ActorComponent;
import rsp.actor.ui.ActorComponentContext;
import rsp.component.*;
import rsp.http.PageApplication;
import rsp.http.WebServer;
import rsp.page.events.GenericTaskEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.dsl.Html.*;

class SharedActorComponentTests {
    @Test
    void commitDuringInitialRenderingIsCaughtAndUnmountOnlyDetachesTheView() throws Exception {
        var type = ActorType.named("shared-render", Integer.class, (String key) -> key);
        var definition = ActorDefinition.<Integer, Integer>builder(type).initialState(_ -> 0)
                .behavior(ActorBehavior.sync((_, state, value) -> ActorEffect.state(state + value))).build();
        try (var actors = LocalActorSystem.builder().register(definition).executor(Runnable::run).build()) {
            actors.start();
            var owner = actors.createOwned(type.id("one"), definition);
            var once = new AtomicBoolean();
            var segment = new AtomicReference<ComponentSegment<Integer>>();
            var commands = new AtomicReference<CommandsEnqueue>();
            var update = new CompletableFuture<Integer>();
            var updates = new AtomicInteger();
            var afterVeto = new CompletableFuture<Integer>();
            var component = new ActorComponent<Integer, Integer>() {
                @Override protected ActorBinding<Integer, Integer> binding(ActorComponentContext context) {
                    return ActorBinding.existing(owner.view());
                }
                @Override public ComponentView<Integer, Integer> componentView() {
                    return _ -> state -> {
                        if (once.compareAndSet(false, true)) owner.view().ref().tell(10);
                        return html(head(title("shared")), body(p("state " + state)));
                    };
                }
                @Override public void onMounted(ComponentSegment<Integer> current, ComponentCompositeKey id,
                                                Integer state, CommandsEnqueue enqueue, StateUpdater<Integer> updater) {
                    segment.set(current);
                    commands.set(enqueue);
                }
                @Override public boolean onBeforeUpdated(Integer state, CommandsEnqueue enqueue) {
                    return state != 11;
                }
                @Override public void onUpdated(ComponentCompositeKey id, Integer oldState, Integer state,
                                                StateUpdater<Integer> updater) {
                    updates.incrementAndGet();
                    update.complete(state);
                    if (state == 12) afterVeto.complete(state);
                }
            };
            try (var server = new WebServer(0).pageApplication(PageApplication.withLifecycle(actors,
                    _ -> rsp.http.HttpResponse.status(rsp.http.HttpStatus.NOT_FOUND).build()))
                    .page("/", (_, _) -> component); var client = HttpClient.newHttpClient()) {
                server.start();
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/")).GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("state 0"));
                assertEquals(10, update.get(5, TimeUnit.SECONDS));
                owner.view().ref().tell(1); // The component vetoes state 11.
                var vetoProcessed = new CompletableFuture<Void>();
                commands.get().offer(new GenericTaskEvent(() -> vetoProcessed.complete(null)));
                vetoProcessed.get(5, TimeUnit.SECONDS);
                assertEquals(1, updates.get());
                owner.view().ref().tell(1);
                assertEquals(12, afterVeto.get(5, TimeUnit.SECONDS));
                var unmounted = new CompletableFuture<Void>();
                commands.get().offer(new GenericTaskEvent(() -> {
                    segment.get().unmount();
                    unmounted.complete(null);
                }));
                unmounted.get(5, TimeUnit.SECONDS);
                owner.view().ref().tell(10);
                var barrier = new CompletableFuture<Void>();
                commands.get().offer(new GenericTaskEvent(() -> barrier.complete(null)));
                barrier.get(5, TimeUnit.SECONDS);
                assertTrue(owner.view().snapshot().active());
                assertEquals(22, owner.view().state());
                assertEquals(2, updates.get());
                var remounted = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, remounted.statusCode());
                assertTrue(remounted.body().contains("state 22"));
            }
        }
    }
}
