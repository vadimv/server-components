package rsp.component;

import rsp.dom.Event;
import rsp.page.PageRendering;
import rsp.page.RenderContextFactory;
import rsp.server.Path;
import rsp.server.RemoteOut;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.json.JsonDataType;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class RelativeUrlStateComponent<S> extends Component<S> {

    private static final String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";

    protected final BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl;
    protected final Function<RelativeUrl, S> relativeUrlToState;

    private volatile RelativeUrl relativeUrl;

    public RelativeUrlStateComponent(final ComponentCompositeKey key,
                                     final RelativeUrl relativeUrl,
                                     final ComponentStateSupplier<S> resolveStateSupplier,
                                     final ComponentView<S> componentView,
                                     final ComponentCallbacks<S> componentCallbacks,
                                     final RenderContextFactory renderContextFactory,
                                     final RemoteOut remotePageMessages,
                                     final BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl,
                                     final Function<RelativeUrl, S> relativeUrlToState,
                                     final Object sessionLock) {
        super(key,
              resolveStateSupplier,
              componentView,
              componentCallbacks,
              renderContextFactory,
              remotePageMessages,
              sessionLock);
        this.relativeUrl = relativeUrl;
        this.stateToRelativeUrl = Objects.requireNonNull(stateToRelativeUrl);
        this.relativeUrlToState = Objects.requireNonNull(relativeUrlToState);
    }

    @Override
    protected void onInitiallyRendered(ComponentCompositeKey key, S state, StateUpdate<S> stateUpdate) {}

    @Override
    protected void onUpdateRendered(ComponentCompositeKey key, S oldState, S state, StateUpdate<S> stateUpdate) {
        addEvent(PageRendering.WINDOW_DOM_PATH,
                 HISTORY_ENTRY_CHANGE_EVENT_NAME,
                 eventContext -> stateUpdate.setStateWhenComplete(relativeUrlToState.apply(extractRelativeUrl(eventContext.eventObject()))),
                true,
                 Event.NO_MODIFIER);

        final RelativeUrl newRelativeUrl = stateToRelativeUrl.apply(state, relativeUrl);
        if (!newRelativeUrl.equals(relativeUrl)) {
            relativeUrl = newRelativeUrl;
            remotePageMessages.pushHistory(relativeUrl.path().toString());
        }
    }

    private static RelativeUrl extractRelativeUrl(final JsonDataType.Object eventObject) {
        final Path path = eventObject.value("path").map(p -> Path.of(p.asJsonString().value()))
                .orElseThrow(() -> new JsonDataType.JsonException("The 'componentPath' property not found in the event object" + eventObject));
        final Query query = eventObject.value("query").map(q -> new Query(q.asJsonString().value()))
                .orElseThrow(() -> new JsonDataType.JsonException("The 'query' property not found in the event object" + eventObject));
        final Fragment fragment = eventObject.value("fragment").map(f -> new Fragment(f.asJsonString().value()))
                .orElseThrow(() -> new JsonDataType.JsonException("The 'fragment' property not found in the event object" + eventObject));
        return new RelativeUrl(path, query, fragment);
    }
}
