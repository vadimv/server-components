package rsp.examples.hnapi;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class JettyHn {

    public static void main(String[] args) throws Exception {
        final Component<State> render = useState ->
                html(
                        body(
                                div(text("TODO tracker")),
                                of(CollectionUtils.zipWithIndex(Arrays.stream(useState.get().strories)).map(story ->
                                        div(
                                                span(text(story.getValue().id + " " + story.getValue().name))
                                        ))
                                )));
        final HnApiService hnApi = new HnApiService();
        final var server = new JettyServer(new App<State>(8080,
                                                    "",
                                                    request -> hnApi.storiesIds()
                                                                    .thenCompose(l -> sequence(l.stream()
                                                                                       .map(s-> hnApi.story(s)).collect(Collectors.toList())))
                                                                    .thenApply(stories -> new State(stories.toArray(new State.Story[0])))        ,
                                                    render));
        server.start();
        server.join();
    }

    static<T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> listOfCompletableFutures) {
        return CompletableFuture.allOf(listOfCompletableFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> listOfCompletableFutures.stream()
                                                        .map(CompletableFuture::join)
                                                        .collect(Collectors.toList()));
    }
}
