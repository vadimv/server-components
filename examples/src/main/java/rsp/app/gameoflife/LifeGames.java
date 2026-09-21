package rsp.app.gameoflife;

import rsp.actor.ActorRef;
import rsp.actor.ActorSystem;
import rsp.page.QualifiedSessionId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Assigns one numeric game ID and actor to each live Life page session. */
final class LifeGames {
    record Game(long id, ActorRef<LifeGame.Command> ref) { }

    private final ActorSystem actors;
    private final Map<QualifiedSessionId, Game> bySession = new HashMap<>();
    private final Map<Long, Game> byId = new HashMap<>();
    private long nextId;

    LifeGames(ActorSystem actors) {
        this.actors = Objects.requireNonNull(actors);
    }

    synchronized Game open(QualifiedSessionId sessionId) {
        Objects.requireNonNull(sessionId);
        Game existing = bySession.get(sessionId);
        if (existing != null) {
            return existing;
        }
        if (nextId == Long.MAX_VALUE) {
            throw new IllegalStateException("Game ID space exhausted");
        }
        long id = ++nextId;
        Game game = new Game(id, actors.ref(LifeGame.TYPE, id));
        bySession.put(sessionId, game);
        byId.put(id, game);
        return game;
    }

    synchronized Optional<Game> find(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    synchronized List<Game> all() {
        List<Game> games = new ArrayList<>(byId.values());
        games.sort(Comparator.comparingLong(Game::id));
        return List.copyOf(games);
    }

    void close(QualifiedSessionId sessionId) {
        Game game;
        synchronized (this) {
            game = bySession.remove(Objects.requireNonNull(sessionId));
            if (game != null) {
                byId.remove(game.id());
            }
        }
        if (game != null) {
            game.ref().tell(new LifeGame.Close());
        }
    }
}
