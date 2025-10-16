package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.Event;
import rsp.dom.TreePositionPath;
import rsp.page.PageObjects;
import rsp.page.PageRendering;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.RemoteCommand;
import rsp.page.events.SessionEvent;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.function.Consumer;

public class SessionObjectPathDispatcherComponentDefinition<S> extends StatefulComponentDefinition<S> {
    private static final String RELATIVE_URL_KEY_NAME = SessionObjectPathDispatcherComponentDefinition.class.getName() + ".relativeUrl";
    private static final String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";
    private final RelativeUrl initialRelativeUrl;
    private final Map<String, Integer> keysIndices = new HashMap<>();
    private final Map<Integer, String> indicesKeys = new HashMap<>();
    private final StatefulComponentDefinition<S> componentDefinition;
    private final String[] keys;

    public SessionObjectPathDispatcherComponentDefinition(RelativeUrl initialRelativeUrl, StatefulComponentDefinition<S> componentDefinition, String... keys) {
        super(SessionObjectPathDispatcherComponentDefinition.class);
        this.initialRelativeUrl = Objects.requireNonNull(initialRelativeUrl);
        this.componentDefinition = Objects.requireNonNull(componentDefinition);
        this.keys = keys;
        for (int i = 0; i < keys.length; i++) {
            keysIndices.put(keys[i], i);
            indicesKeys.put(i, keys[i]);
        }
    }

    @Override
    public ComponentStateSupplier<S> stateSupplier() {
        return componentDefinition.stateSupplier();
    }

    @Override
    public ComponentView<S> componentView() {
        return componentDefinition.componentView();
    }

    public void onRelativeUrlChanged(PageObjects.ComponentContext sessionBag, RelativeUrl relativeUrl) {
        // extract fields and put them in the session bag
    }

    @Override
    public Component<S> createComponent(QualifiedSessionId sessionId,
                                        TreePositionPath componentPath,
                                        RenderContextFactory renderContextFactory,
                                        PageObjects sessionObjects,
                                        Consumer<SessionEvent> commandsEnqueue) {
        final ComponentCompositeKey key = new ComponentCompositeKey(sessionId, componentType, componentPath);

        return new Component<>(key,
                            stateSupplier(),
                            componentView(),
                            new ComponentCallbacks<>(onComponentMountedCallback(),
                                                    onComponentUpdatedCallback(),
                                                    onComponentUnmountedCallback()),
                            renderContextFactory,
                            sessionObjects,
                            commandsEnqueue) {

            @Override
            protected void onBeforeComponentMount() {
                if (!sessionObjects.containsKey(RELATIVE_URL_KEY_NAME)) {
                    sessionObjects.put(RELATIVE_URL_KEY_NAME, initialRelativeUrl);
                    for(int i = 0; i < keys.length;i++) {
                        sessionObjects.put(keys[i], initialRelativeUrl.path().get(i));
                    }
                }

            }

            @Override
            protected void onComponentMounted(S state) {
                subscribeForBrowserHistoryEvents();
                subscribeForSessionObjectsUpdates();
            }

            private void subscribeForSessionObjectsUpdates() {
                final PageObjects.ComponentContext sessionObjectsBag = sessionObjects.ofComponent(componentId);
                for (final String key : keys) {
                    sessionObjectsBag.onValueUpdated(key, obj -> {
                        if (obj instanceof String value) {
                            final RelativeUrl newRelativeUrl = updatedRelativeUrlFor(key, value);
                            sessionObjects.put(RELATIVE_URL_KEY_NAME, newRelativeUrl);
                            this.commandsEnqueue.accept(new RemoteCommand.PushHistory(newRelativeUrl.path().toString()));
                        } else {
                            throw new IllegalStateException("A path element session object is not a string");
                        }
                    });
                }

            }

            private RelativeUrl updatedRelativeUrlFor(String key, String value) {
                final int pathElementIndex = keysIndices.get(key);
                final RelativeUrl oldRelativeUrl = (RelativeUrl) sessionObjects.get(RELATIVE_URL_KEY_NAME);
                assert oldRelativeUrl != null;
                final List<String> pathElements = new ArrayList<>();
                for (int j = 0; j < oldRelativeUrl.path().size(); j++) {
                    pathElements.add(oldRelativeUrl.path().get(j));
                }
                pathElements.set(pathElementIndex, value);
                final Path newPath = new Path(true, pathElements.toArray(new String[0]));

                return new RelativeUrl(newPath, oldRelativeUrl.query(), oldRelativeUrl.fragment());
            }

            private void subscribeForBrowserHistoryEvents() {
                this.addEvent(PageRendering.WINDOW_DOM_PATH,
                              HISTORY_ENTRY_CHANGE_EVENT_NAME,
                             eventContext -> {
                                 final var session = sessionObjects.ofComponent(componentId);
                                 final RelativeUrl newRelativeUrl = extractRelativeUrl(eventContext.eventObject());
                                 final RelativeUrl oldRelativeUrl = (RelativeUrl) session.get(RELATIVE_URL_KEY_NAME);
                                 session.put(RELATIVE_URL_KEY_NAME, newRelativeUrl);

                                 for (int i=0; i< oldRelativeUrl.path().size(); i++) {
                                     final String oldElement = oldRelativeUrl.path().get(i);
                                     final String newElement = newRelativeUrl.path().get(i);
                                     if (!oldElement.equals(newElement)) {
                                         session.put(indicesKeys.get(i), newElement);
                                     }
                                 }
                             },
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

        };
    }


}
