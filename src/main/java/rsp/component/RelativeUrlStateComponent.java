package rsp.component;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.LivePageSession;
import rsp.page.RenderContextFactory;
import rsp.server.Path;
import rsp.server.RemoteOut;
import rsp.server.http.Fragment;
import rsp.server.http.PageStateOrigin;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.json.JsonDataType;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class RelativeUrlStateComponent<S> extends Component<S> {

    protected final BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl;
    protected final Function<RelativeUrl, CompletableFuture<? extends S>> relativeUrlToState;
    protected final PageStateOrigin pageStateOrigin;

    public RelativeUrlStateComponent(final ComponentCompositeKey key,
                                     final Supplier<CompletableFuture<? extends S>> resolveStateSupplier,
                                     final MountCallback<S> componentDidMount,
                                     final ComponentView<S> componentView,
                                     final StateAppliedCallback<S> componentDidUpdate,
                                     final UnmountCallback<S> componentWillUnmount,
                                     final RenderContextFactory renderContextFactory,
                                     final RemoteOut remotePageMessages,
                                     final BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl,
                                     final Function<RelativeUrl, CompletableFuture<? extends S>> relativeUrlToState,
                                     final PageStateOrigin pageStateOrigin) {
        super(key,
              resolveStateSupplier,
              componentDidMount,
              componentView,
              componentDidUpdate,
              componentWillUnmount,
              renderContextFactory,
              remotePageMessages);
        this.stateToRelativeUrl = Objects.requireNonNull(stateToRelativeUrl);
        this.relativeUrlToState = Objects.requireNonNull(relativeUrlToState);
        this.pageStateOrigin = Objects.requireNonNull(pageStateOrigin);
    }

    @Override
    protected void componentDidMount2(ComponentCompositeKey key, S state, StateUpdate<S> stateUpdate, ComponentRenderContext componentRenderContext) {
        addEvent(new Event(new Event.Target(LivePageSession.HISTORY_ENTRY_CHANGE_EVENT_NAME,
                                            VirtualDomPath.WINDOW),
                           eventContext -> stateUpdate.setStateWhenComplete(relativeUrlToState.apply(extractRelativeUrl(eventContext.eventObject()))),
                          true,
                           Event.NO_MODIFIER));
    }

    @Override
    protected void componentDidUpdate2(ComponentCompositeKey key, S oldState, S state, StateUpdate<S> stateUpdate, ComponentRenderContext componentRenderContext) {
        final RelativeUrl oldRelativeUrl = componentRenderContext.getRelativeUrl();
        final RelativeUrl newRelativeUrl = stateToRelativeUrl.apply(state, oldRelativeUrl);
        if (!newRelativeUrl.equals(oldRelativeUrl)) {
            setRelativeUrl(newRelativeUrl);
        }
    }

    private void setRelativeUrl(RelativeUrl relativeUrl) {
        pageStateOrigin.setRelativeUrl(relativeUrl);
        remotePageMessages.pushHistory(relativeUrl.path().toString());
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
