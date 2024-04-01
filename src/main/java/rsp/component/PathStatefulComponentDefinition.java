package rsp.component;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.LivePageSession;
import rsp.page.RenderContext;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.TriConsumer;
import rsp.util.json.JsonDataType;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class PathStatefulComponentDefinition<S> extends StatefulComponentDefinition<S> {

    public PathStatefulComponentDefinition(final Object key) {
        super(key);
    }

    @Override
    protected BiFunction<S, Path, Path> state2pathFunction() {
        return (__, path) -> path;
    }

    protected abstract Function<RelativeUrl, CompletableFuture<? extends S>> relativeUrlToStateFunction();

    @Override
    protected TriConsumer<S, NewState<S>, RenderContext> beforeRender() {
        return (state, newState, renderContext) -> {
            renderContext.addEvent(VirtualDomPath.WINDOW,
                                   LivePageSession.HISTORY_ENTRY_CHANGE_EVENT_NAME,
                                   eventContext -> newState.applyWhenComplete(relativeUrlToStateFunction().apply(getRelativeUrl(eventContext.eventObject()))),
                                  true,
                                   Event.NO_MODIFIER);
        };
    }

    private static RelativeUrl getRelativeUrl(final JsonDataType.Object eventObject) {
        final Path path = eventObject.value("path").map(p -> Path.of(p.toString()))
                .orElseThrow(() -> new JsonDataType.JsonException("The 'path' property not found in the event object" + eventObject));
        final Query query = eventObject.value("query").map(q -> new Query(q.toString()))
                .orElseThrow(() -> new JsonDataType.JsonException("The 'query' property not found in the event object" + eventObject));
        final Fragment fragment = eventObject.value("fragment").map(f -> new Fragment(f.toString()))
                .orElseThrow(() -> new JsonDataType.JsonException("The 'fragment' property not found in the event object" + eventObject));
        return new RelativeUrl(path, query, fragment);
    }
}
