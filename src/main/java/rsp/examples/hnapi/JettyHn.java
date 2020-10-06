package rsp.examples.hnapi;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.lang.Float.parseFloat;
import static rsp.dsl.Html.*;

public class JettyHn {

    public static void main(String[] args) throws Exception {
        final HnApiService hnApi = new HnApiService();
        final var bodyRef = createRef();
        final var windowRef = window().ref();
        final Component<State> render = useState ->
            html(
                    body(bodyRef,
                        div(text("Hacker News")),
                        of(CollectionUtils.zipWithIndex(Arrays.stream(useState.get().stories)).map(story ->
                                div(
                                        span(text(story.getValue().id + " " + story.getValue().name))
                                ))
                          ),
                        window().event("scroll", c -> {
                            final var windowProps = c.props(window());
                            final var bodyProps = c.props(bodyRef);
                            windowProps.get("innerHeight")
                                       .thenCompose(innerHeight -> windowProps.get("pageYOffset")
                                       .thenCompose(pageYOffset -> bodyProps.get("offsetHeight")
                                       .thenAccept(offsetHeight -> {
                                           if ((parseFloat(innerHeight) + parseFloat(pageYOffset)) >= parseFloat(offsetHeight)) {
                                               final State currentState = useState.get();
                                               final int newPageNum = currentState.pageNum + 1;
                                               final List<Integer> newStoriesIds = pageIds(Arrays.stream(currentState.storiesIds).boxed().collect(Collectors.toList()),
                                                                                           newPageNum,
                                                                                           HnApiService.PAGE_SIZE);
                                               final CompletableFuture<State> newState = hnApi.stories(newStoriesIds)
                                                                                              .thenApply(r -> new State(currentState.storiesIds,
                                                                                                                           concatArrays(currentState.stories,
                                                                                                                                        r.toArray(State.Story[]::new)),
                                                                                                                           newPageNum));
                                               newState.thenAccept(state -> useState.accept(state));
                                           }
                                       })));
                        }).debounce(500)
                    )
                );

        final var server = new JettyServer(8080,"", new App<State>(
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
