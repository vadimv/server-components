package rsp.component;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.LivePageSession;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.TriConsumer;
import rsp.util.json.JsonDataType;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class PathStatefulComponentDefinition<S> extends StatefulComponentDefinition<S> {

    public PathStatefulComponentDefinition(final Object key) {
        super(key);
    }

    protected abstract BiFunction<S, Path, Path> state2pathFunction();

    protected abstract Function<RelativeUrl, CompletableFuture<? extends S>> relativeUrlToStateFunction();

    @Override
    protected BeforeRenderCallback<S> beforeRenderCallback() {
        return (key, state, newState, renderContext) -> {
                renderContext.addEvent(VirtualDomPath.WINDOW,
                                       LivePageSession.HISTORY_ENTRY_CHANGE_EVENT_NAME,
                                       eventContext -> newState.applyWhenComplete(relativeUrlToStateFunction().apply(extractRelativeUrl(eventContext.eventObject()))),
                                      true,
                                       Event.NO_MODIFIER);
        };
    }

    @Override
    protected StateAppliedCallback<S> newStateAppliedCallback() {
        return (key, state, renderContext) -> {
            final RelativeUrl oldRelativeUrl = renderContext.getRelativeUrl();
            final Path oldPath = oldRelativeUrl.path();
            final Path newPath = state2pathFunction().apply(state, oldPath);
            if (!newPath.equals(oldPath)) {
                renderContext.setRelativeUrl(new RelativeUrl(newPath, oldRelativeUrl.query(), oldRelativeUrl.fragment()));
            }
        };
    }

    private static RelativeUrl extractRelativeUrl(final JsonDataType.Object eventObject) {
        final Path path = eventObject.value("path").map(p -> Path.of(p.toString()))
                .orElseThrow(() -> new JsonDataType.JsonException("The 'path' property not found in the event object" + eventObject));
        final Query query = eventObject.value("query").map(q -> new Query(q.toString()))
                .orElseThrow(() -> new JsonDataType.JsonException("The 'query' property not found in the event object" + eventObject));
        final Fragment fragment = eventObject.value("fragment").map(f -> new Fragment(f.toString()))
                .orElseThrow(() -> new JsonDataType.JsonException("The 'fragment' property not found in the event object" + eventObject));
        return new RelativeUrl(path, query, fragment);
    }
}
