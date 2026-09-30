package rsp.app.gameoflife;

import rsp.actor.runtime.LocalActorSystem;
import rsp.actor.ui.ActorBinding;
import rsp.actor.ui.ActorRenderPolicy;
import rsp.actor.ui.ActorRenderScheduler;
import rsp.actor.ui.PageActorDirectory;
import rsp.actor.ui.PageActorRuntime;
import rsp.application.ApplicationContext;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.PageApplication;
import rsp.http.StaticResources;
import rsp.http.WebServer;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

/** Independent page games and application-owned shared simulations, with UI and HTTP facades. */
public final class Life {
    private Life() {
    }

    public static void main(String[] args) {
        WebServer server = server(8082, RandomGenerator.getDefault());
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(server::stop));
        server.start();
        try {
            server.join();
        } finally {
            server.stop();
        }
    }

    static WebServer server(int port, RandomGenerator random) {
        return server(port, random, 128);
    }

    static WebServer server(int port, RandomGenerator random, int sharedCapacity) {
        var game = LifeGame.definition(random);
        PageActorRuntime actors = PageActorRuntime.builder().build();
        LocalActorSystem sharedActors = LocalActorSystem.builder().register(game).build();
        ActorRenderScheduler scheduler = new ActorRenderScheduler();
        ActorRenderPolicy rendering = ActorRenderPolicy.throttled(Duration.ofMillis(50), scheduler);
        AtomicLong nextId = new AtomicLong();
        java.util.function.LongSupplier ids = () -> nextId.updateAndGet(Math::incrementExact);
        Simulations simulations = new Simulations(sharedActors, game, ids, sharedCapacity, Duration.ofSeconds(2), scheduler);
        ApplicationContext application = ApplicationContext.builder()
                .service(PageActorRuntime.class, actors)
                .service(LocalActorSystem.class, sharedActors)
                .service(ActorRenderScheduler.class, scheduler)
                .service(Simulations.class, simulations)
                .build();
        PageActorDirectory<Long, LifeGame.Command> games = new PageActorDirectory<>(
                actors, LifeGame.TYPE, ids::getAsLong);
        LifeComponent component = new LifeComponent(ActorBinding.page(games, game), rendering);
        return new WebServer(port)
                .pageApplication(PageApplication.withLifecycle(application,
                        _ -> HttpResponse.status(HttpStatus.NOT_FOUND).build()))
                .page("/", (_, _) -> component)
                .routes(rsp.http.HttpRouter.builder().include(LifeRoutes.router(actors, games))
                        .include(SharedLifeRoutes.router(simulations, rendering)).build())
                .staticResources(new StaticResources(
                        new File("src/main/java/rsp/app/gameoflife"), "/res/"));
    }
}
