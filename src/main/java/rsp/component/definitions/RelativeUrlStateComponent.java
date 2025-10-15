package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.Event;
import rsp.page.PageObjects;
import rsp.page.PageRendering;
import rsp.page.RenderContextFactory;
import rsp.page.events.RemoteCommand;
import rsp.page.events.SessionEvent;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.json.JsonDataType;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class RelativeUrlStateComponent<S> extends Component<S> {
    private static final String RELATIVE_URL_KEY_NAME = "relativeUrl";
    private static final String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";

    protected final BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl;
    protected final Function<RelativeUrl, S> relativeUrlToState;

    public RelativeUrlStateComponent(final ComponentCompositeKey key,
                                     final RelativeUrl relativeUrl,
                                     final ComponentStateSupplier<S> resolveStateSupplier,
                                     final ComponentView<S> componentView,
                                     final ComponentCallbacks<S> componentCallbacks,
                                     final RenderContextFactory renderContextFactory,
                                     final PageObjects sessionObjects,
                                     final Consumer<SessionEvent> commandsEnqueue,
                                     final BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl,
                                     final Function<RelativeUrl, S> relativeUrlToState) {
        super(key,
              resolveStateSupplier,
              componentView,
              componentCallbacks,
              renderContextFactory,
              sessionObjects,
              commandsEnqueue);

        if (!sessionObjects.containsKey(RELATIVE_URL_KEY_NAME)) {
            sessionObjects.put(RELATIVE_URL_KEY_NAME, relativeUrl);
        }

        this.stateToRelativeUrl = Objects.requireNonNull(stateToRelativeUrl);
        this.relativeUrlToState = Objects.requireNonNull(relativeUrlToState);
    }

    @Override
    protected void onInitiallyRendered(ComponentCompositeKey key, S state, StateUpdate<S> stateUpdate) {
        addHistoryEvent(stateUpdate);
    }

    @Override
    protected void onUpdateRendered(ComponentCompositeKey key, S oldState, S state, StateUpdate<S> stateUpdate) {
        addHistoryEvent(stateUpdate);

        final RelativeUrl relativeUrl = (RelativeUrl) sessionObjects.get(RELATIVE_URL_KEY_NAME);
        final RelativeUrl newRelativeUrl = stateToRelativeUrl.apply(state, relativeUrl);
        if (!newRelativeUrl.equals(relativeUrl)) {
            sessionObjects.put(RELATIVE_URL_KEY_NAME, newRelativeUrl);
            commandsEnqueue.accept(new RemoteCommand.PushHistory(newRelativeUrl.path().toString()));
        }
    }

    private void addHistoryEvent(StateUpdate<S> stateUpdate) {
        addEvent(PageRendering.WINDOW_DOM_PATH,
                HISTORY_ENTRY_CHANGE_EVENT_NAME,
                eventContext -> stateUpdate.setStateWhenComplete(relativeUrlToState.apply(extractRelativeUrl(eventContext.eventObject()))),
                true,
                Event.NO_MODIFIER);
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
