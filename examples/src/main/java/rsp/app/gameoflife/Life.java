package rsp.app.gameoflife;

import rsp.actor.ActorGateway;
import rsp.actor.ui.PageActorDirectory;
import rsp.actor.ui.PageActorRuntime;
import rsp.application.ApplicationContext;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.PageApplication;
import rsp.http.StaticResources;
import rsp.http.WebServer;

import java.io.File;
import java.util.random.RandomGenerator;

/** A Game of Life actor per live page session, with HTTP routes for active games. */
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
        var game = LifeGame.definition(random);
        PageActorRuntime actors = PageActorRuntime.builder().build();
        ApplicationContext application = ApplicationContext.builder()
                .service(ActorGateway.class, actors)
                .build();
        PageActorDirectory<Long, LifeGame.Command> games = PageActorDirectory.numbered(
                actors, LifeGame.TYPE);
        LifeComponent component = new LifeComponent(games, game);
        return new WebServer(port)
                .pageApplication(PageApplication.withLifecycle(application,
                        _ -> HttpResponse.status(HttpStatus.NOT_FOUND).build()))
                .page("/", (_, _) -> component)
                .routes(LifeRoutes.router(actors, games))
                .staticResources(new StaticResources(
                        new File("src/main/java/rsp/app/gameoflife"), "/res/"));
    }
}
