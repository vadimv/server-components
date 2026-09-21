package rsp.app.gameoflife;

import rsp.actor.ActorSystem;
import rsp.actor.runtime.LocalActorSystem;
import rsp.application.ApplicationContext;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.PageApplication;
import rsp.http.StaticResources;
import rsp.http.WebServer;

import java.io.File;
import java.util.random.RandomGenerator;

/** A shared Game of Life actor viewed by live UI pages and ordinary HTTP routes. */
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
        LocalActorSystem actors = LocalActorSystem.builder()
                .register(LifeGame.definition(random))
                .build();
        ApplicationContext application = ApplicationContext.builder()
                   .service(ActorSystem.class, actors)
                .build();
        LifeComponent component = new LifeComponent(actors.ref(LifeGame.TYPE, LifeGame.ID));
        return new WebServer(port)
                .pageApplication(PageApplication.withLifecycle(application,
                        _ -> HttpResponse.status(HttpStatus.NOT_FOUND).build()))
                .page("/", (_, _) -> component)
                .routes(LifeRoutes.router(actors))
                .staticResources(new StaticResources(
                        new File("src/main/java/rsp/app/gameoflife"), "/res/"));
    }
}
