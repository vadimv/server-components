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
        final HnApiService hnApi = new HnApiService();

        final Component<State> render = useState ->
                html(
                        body(
                                div(text("Hacker News")),
                                of(CollectionUtils.zipWithIndex(Arrays.stream(useState.get().stories)).map(story ->
                                        div(
                                                span(text(story.getValue().id + " " + story.getValue().name))
                                        ))
                                  ),
                                event("click", c -> {
                                    final State currentState = useState.get();
                                    final int newPageNum = currentState.pageNum + 1;
                                    final List<Integer> newStoriesIds = pageIds(Arrays.stream(currentState.storiesIds).boxed().collect(Collectors.toList()),
                                                                                newPageNum,
                                                                                HnApiService.PAGE_SIZE);
                                    final CompletableFuture<State> newState = hnApi.stories(newStoriesIds)
                                                                                   .thenApply(r -> new State(currentState.storiesIds,
                                                                                                             concatArrays(currentState.stories, r.toArray(State.Story[]::new)),
                                                                                                             newPageNum));
                                    newState.thenAccept(state -> useState.accept(state));
                                })

                        )
                    );

        final var server = new JettyServer(new App<State>(8080,
                                                    "",
                                                    request -> hnApi.storiesIds()
                                                                    .thenCompose(ids -> hnApi.stories(pageIds(ids, 0, HnApiService.PAGE_SIZE))
                                                                                        .thenApply(r -> new State(ids.stream().mapToInt(Integer::intValue).toArray(),
                                                                                                              r.toArray(State.Story[]::new),
                                                                                                             0))),
                                                    render));
        server.start();
        server.join();
    }

    private static List<Integer> pageIds(List<Integer> storiesIds, int pageNum, int pageSize) {
        return storiesIds.subList(pageNum * pageSize, (pageNum + 1) * pageSize);
    }

    private static <T> T[] concatArrays(T[] first, T[] second) {
        final T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

}
