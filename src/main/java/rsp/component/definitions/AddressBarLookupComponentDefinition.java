package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.Event;
import rsp.dom.TreePositionPath;
import rsp.page.Lookup;
import rsp.page.PageRendering;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.DomEvent;
import rsp.page.events.RemoteCommand;
import rsp.page.events.SessionEvent;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.function.Consumer;

public class AddressBarLookupComponentDefinition<S> extends StatefulComponentDefinition<S> {
    private static final String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";
    private final RelativeUrl initialRelativeUrl;


    private final StatefulComponentDefinition<S> componentDefinition;
    private final List<String> pathElementsKeys;
    private final List<ParameterNameKey> queryParametersNameKeys;

    AddressBarLookupComponentDefinition(RelativeUrl initialRelativeUrl,
                                        StatefulComponentDefinition<S> componentDefinition,
                                        List<String> pathElementsKeys,
                                        List<ParameterNameKey> queryParametersNameKeys) {
        super(AddressBarLookupComponentDefinition.class);
        this.initialRelativeUrl = Objects.requireNonNull(initialRelativeUrl);
        this.componentDefinition = Objects.requireNonNull(componentDefinition);
        this.pathElementsKeys = Objects.requireNonNull(pathElementsKeys);
        this.queryParametersNameKeys = Objects.requireNonNull(queryParametersNameKeys);
    }

    public static <S> AddressBarLookupComponentDefinition<S> of(RelativeUrl initialRelativeUrl, StatefulComponentDefinition<S> componentDefinition) {
        return new AddressBarLookupComponentDefinition<>(initialRelativeUrl, componentDefinition, List.of(), List.of());
    }

    public AddressBarLookupComponentDefinition<S> withPathElement(String key) {
        final List<String> l = new ArrayList<>(this.pathElementsKeys);
        l.add(key);
        return new AddressBarLookupComponentDefinition<>(this.initialRelativeUrl, this.componentDefinition, l, this.queryParametersNameKeys);
    }

    public AddressBarLookupComponentDefinition<S> withQueryParameter(String parameterName, String key) {
        final List<ParameterNameKey> l = new ArrayList<>(queryParametersNameKeys);
        l.add(new ParameterNameKey(parameterName, key));
        return new AddressBarLookupComponentDefinition<>(this.initialRelativeUrl, this.componentDefinition, this.pathElementsKeys, l);
    }

    @Override
    public ComponentStateSupplier<S> stateSupplier() {
        return componentDefinition.stateSupplier();
    }

    @Override
    public ComponentView<S> componentView() {
        return componentDefinition.componentView();
    }


    @Override
    public Component<S> createComponent(QualifiedSessionId sessionId,
                                        TreePositionPath componentPath,
                                        RenderContextFactory renderContextFactory,
                                        Lookup sessionObjects,
                                        Consumer<SessionEvent> commandsEnqueue) {
        final ComponentCompositeKey componentId = new ComponentCompositeKey(sessionId, componentType, componentPath);// TODO

        // prepare indices for path elements session keys
        final Map<String, Integer> pathElementsKeysIndices = new HashMap<>();
        final Map<Integer, String> pathElementsIndicesKeys = new HashMap<>();
        for (int i = 0; i < pathElementsKeys.size(); i++) {
            pathElementsKeysIndices.put(pathElementsKeys.get(i), i);
            pathElementsIndicesKeys.put(i, pathElementsKeys.get(i));
        }

        // prepare a map for query parameters
        final Map<String, String> parameterNameKeyMap = new HashMap<>();
        for (ParameterNameKey p: queryParametersNameKeys) {
            parameterNameKeyMap.put(p.parameterName(), p.key());
        }

        return new Component<>(componentId,
                               stateSupplier(),
                               componentView(),
                               new ComponentCallbacks<>(onComponentMountedCallback(),
                                                        onComponentUpdatedCallback(),
                                                        onComponentUnmountedCallback()),
                               renderContextFactory,
                               sessionObjects,
                               commandsEnqueue) {

            private RelativeUrl relativeUrl;

            @Override
            protected void onBeforeInitiallyRendered() {
                relativeUrl = initialRelativeUrl;
                for (int i = 0; i < pathElementsKeys.size(); i++) {
                    lookup.put(pathElementsKeys.get(i), initialRelativeUrl.path().get(i));
                }

                for (ParameterNameKey queryParametersNameKey : queryParametersNameKeys) {
                    final Optional<String> optionalParameter = initialRelativeUrl.query().parameterValue(queryParametersNameKey.parameterName());
                    optionalParameter.ifPresent(p -> {
                        lookup.put(queryParametersNameKey.key(), p);
                    });
                }
            }

            @Override
            protected void onAfterInitiallyRendered(S state) {
                subscribeForBrowserHistoryEvents();
                subscribeForSessionObjectsUpdates();
            }

            @Override
            protected void onAfterUnmounted(ComponentCompositeKey key, S oldState) {
            }

            private void subscribeForSessionObjectsUpdates() {
                // subscribe for path elements changes
                for (final String pathElementKey : pathElementsKeys) {
                    this.addEventHandler(PageRendering.WINDOW_DOM_PATH,
                                        "stateUpdated." + pathElementKey,
                                        eventContext -> {
                        final String value = eventContext.eventObject().value("value").get().asJsonString().value();
                        final int pathElementIndex = pathElementsKeysIndices.get(pathElementKey);
                        relativeUrl = updatedRelativeUrlForPathElement(relativeUrl, pathElementKey, value, pathElementIndex);
                        this.commandsEnqueue.accept(new RemoteCommand.PushHistory(relativeUrl.toString()));
                    },
                                        true,
                                        Event.NO_MODIFIER);
                }

                // subscribe for query parameters changes
                for (final ParameterNameKey parameterNameKey : queryParametersNameKeys) {
                    this.addEventHandler(PageRendering.WINDOW_DOM_PATH,
                                        "stateUpdated." + parameterNameKey.key(),
                                        eventContext -> {
                        final String value = eventContext.eventObject().value("value").get().asJsonString().value();
                        relativeUrl = updatedRelativeUrlForParameter(relativeUrl, parameterNameKey.key(), value);
                        this.commandsEnqueue.accept(new RemoteCommand.PushHistory(relativeUrl.toString()));

                    },true,
                            Event.NO_MODIFIER);
                }

            }

            private RelativeUrl updatedRelativeUrlForPathElement(RelativeUrl oldRelativeUrl,
                                                                 String pathElementKey,
                                                                 String value, int pathElementIndex) {

                final List<String> pathElements = new ArrayList<>();
                for (int j = 0; j < oldRelativeUrl.path().elementsCount(); j++) {
                    pathElements.add(oldRelativeUrl.path().get(j));
                }
                pathElements.set(pathElementIndex, value);
                final Path newPath = new Path(true, pathElements.toArray(new String[0]));

                return new RelativeUrl(newPath, oldRelativeUrl.query(), oldRelativeUrl.fragment());
            }

            private RelativeUrl updatedRelativeUrlForParameter(RelativeUrl oldRelativeUrl, String parameterName, String parameterValue) {
                final List<Query.Parameter> parameters = new ArrayList<>(oldRelativeUrl.query().parameters());
                int i = 0;
                for (; i < parameters.size(); i++) {
                    if (parameters.get(i).name().equals(parameterName)) break;
                }
                parameters.set(i, new Query.Parameter(parameterName, parameterValue));

                return new RelativeUrl(oldRelativeUrl.path(), new Query(parameters), oldRelativeUrl.fragment());
            }

            private void subscribeForBrowserHistoryEvents() {
                this.addEventHandler(PageRendering.WINDOW_DOM_PATH,
                              HISTORY_ENTRY_CHANGE_EVENT_NAME,
                             eventContext -> {
                                 final var session = lookup.ofComponent(componentId);
                                 final RelativeUrl newRelativeUrl = extractRelativeUrl(eventContext.eventObject());
                                 final RelativeUrl oldRelativeUrl = relativeUrl;
                                 relativeUrl = newRelativeUrl;
                                 // update path elements
                                 for (int i = 0; i < oldRelativeUrl.path().elementsCount(); i++) {
                                     final String oldElement = oldRelativeUrl.path().get(i);
                                     final String newElement = newRelativeUrl.path().get(i);
                                     if (!oldElement.equals(newElement)) {
                                         session.put(pathElementsIndicesKeys.get(i), newElement);
                                         // enqueue history event "historyUndo"
                                         commandsEnqueue.accept(new DomEvent(1, PageRendering.WINDOW_DOM_PATH, "historyUndo." + pathElementsIndicesKeys.get(i),
                                                 new JsonDataType.Object().put("value", new JsonDataType.String(newElement))));
                                         return;
                                     }
                                 }

                                 // update query parameters
                                 for (Query.Parameter parameter: oldRelativeUrl.query().parameters()) {
                                     final Optional<String>  newParameterValue = newRelativeUrl.query().parameterValue(parameter.name());
                                     if (newParameterValue.isPresent() && !newParameterValue.get().equals(parameter.value())) {
                                         session.put(parameterNameKeyMap.get(parameter.name()), newParameterValue.get());
                                         // enqueue history event "historyUndo"
                                         commandsEnqueue.accept(new DomEvent(1, PageRendering.WINDOW_DOM_PATH, "historyUndo." + parameter.name(),
                                                 new JsonDataType.Object().put("value", new JsonDataType.String(newParameterValue.get()))));
                                         return;
                                     }
                                 }
                             },
                             true,
                              Event.NO_MODIFIER);
            }

            private static RelativeUrl extractRelativeUrl(final JsonDataType.Object eventObject) {
                final Path path = eventObject.value("path").map(p -> Path.of(p.asJsonString().value()))
                        .orElseThrow(() -> new JsonDataType.JsonException("The 'componentPath' property not found in the event object" + eventObject));
                final Query query = eventObject.value("query").map(q -> Query.of(q.asJsonString().value()))
                        .orElseThrow(() -> new JsonDataType.JsonException("The 'query' property not found in the event object" + eventObject));
                final Fragment fragment = eventObject.value("fragment").map(f -> new Fragment(f.asJsonString().value()))
                        .orElseThrow(() -> new JsonDataType.JsonException("The 'fragment' property not found in the event object" + eventObject));
                return new RelativeUrl(path, query, fragment);
            }

        };
    }

    private record ParameterNameKey(String parameterName, String key) {
    }
}
