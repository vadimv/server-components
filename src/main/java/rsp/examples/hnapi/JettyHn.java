package rsp.examples.hnapi;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.server.StaticResources;
import rsp.util.Log;
import rsp.util.StreamUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.lang.Float.parseFloat;
import static rsp.dsl.Html.*;
import static rsp.util.ArrayUtils.concat;

/**
 *  A Hacker News API client implementation with 'infinite' scrolling
 */
public class JettyHn {

    public static void main(String[] args) throws Exception {
        final HnApiService hnApi = new HnApiService();
        final var bodyRef = createRef();
        final Component<State> render = useState ->
            html(head(link(attr("rel", "stylesheet"), attr("href","/res/style.css"))),
                    body(bodyRef,
                        div(attr("class", "header"),
                            h3(text("Hacker News"))),
                        div(attr("class", "content"),
                            of(StreamUtils.zipWithIndex(Arrays.stream(useState.get().stories)).map(story ->
                                    div(
                                            span((story.getKey() + 1) + ". "),
                                            a(attr("href", story.getValue().url), text(story.getValue().name))
                                    ))
                              )),
                        window().on("scroll", c -> {
                            final var windowProps = c.props(window());
                            final var bodyProps = c.props(bodyRef);
                            windowProps.getString("innerHeight")
                                       .thenCompose(innerHeight -> windowProps.getString("pageYOffset")
                                       .thenCompose(pageYOffset -> bodyProps.getString("offsetHeight")
                                       .thenAccept(offsetHeight -> {
                                           if ((parseFloat(innerHeight) + parseFloat(pageYOffset)) >= parseFloat(offsetHeight)) {
                                               final State currentState = useState.get();
                                               final int newPageNum = currentState.pageNum + 1;
                                               final List<Integer> newStoriesIds = pageIds(Arrays.stream(currentState.storiesIds).boxed().collect(Collectors.toList()),
                                                                                           newPageNum,
                                                                                           HnApiService.PAGE_SIZE);
                                               final CompletableFuture<State> newState = hnApi.stories(newStoriesIds)
                                                                                              .thenApply(newStories ->
                                                                                                      new State(currentState.storiesIds,
                                                                                                                concat(currentState.stories,
                                                                                                                       newStories.toArray(State.Story[]::new)),
                                                                                                                newPageNum));
                                               useState.accept(newState);
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
                                                    render),
                                                    new StaticResources(new File("src/main/java/rsp/examples/hnapi"),
                                                                   "/res/*"));
        server.start();
        server.join();
    }

    private static List<Integer> pageIds(List<Integer> storiesIds, int pageNum, int pageSize) {
        return storiesIds.subList(pageNum * pageSize, (pageNum + 1) * pageSize);
    }


}
