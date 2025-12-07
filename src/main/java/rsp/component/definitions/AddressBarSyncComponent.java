package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.dsl.Definition;
import rsp.page.PageRendering;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.RemoteCommand;
import rsp.page.events.Command;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static rsp.component.definitions.ContextComponent.STATE_UPDATED_EVENT_PREFIX;
import static rsp.component.definitions.ContextComponent.STATE_VALUE_ATTRIBUTE_NAME;

/**
 * This wrapper component acts as a mediator and synchronizes the current browsers page address bar's path elements and query parameters
 * to the wrapped components subtree states.
 * On an initial rendering, the actual URL path and query parameters state are mapped to the context's fields to be propagated with the components' context to  its subtree components.
 * Later when the bind state changes somewhere in the components down the wrapped subtree, this component is notified and updates the address bar.
 * @see ComponentContext for subtree components with bind state
 */
public class AddressBarSyncComponent extends StatefulComponent<RelativeUrl> {

    /**
     * A browser session history entry change event name
     */
    private static final String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";

    private final RelativeUrl initialRelativeUrl;
    private final Definition subTreeDefinition;
    private final List<PositionKey> pathElementsKeys;
    private final List<ParameterNameKey> queryParametersNameKeys;

    AddressBarSyncComponent(final RelativeUrl initialRelativeUrl,
                            final Definition subTreeDefinition,
                            final List<PositionKey> pathElementsKeys,
                            final List<ParameterNameKey> queryParametersNameKeys) {
        super(AddressBarSyncComponent.class);
        this.initialRelativeUrl = Objects.requireNonNull(initialRelativeUrl);
        this.subTreeDefinition = Objects.requireNonNull(subTreeDefinition);
        this.pathElementsKeys = Objects.requireNonNull(pathElementsKeys);
        this.queryParametersNameKeys = Objects.requireNonNull(queryParametersNameKeys);
    }

    /**
     * Creates a new instance of this type of component.
     * This method is a part of little language to configure path elements an query parameters mappings.
     * @param initialRelativeUrl
     * @param componentDefinition
     * @return a new instance
     * @param <S>
     */
    public static <S> AddressBarSyncComponent of(RelativeUrl initialRelativeUrl, StatefulComponent<S> componentDefinition) {
        return new AddressBarSyncComponent(initialRelativeUrl, componentDefinition, List.of(), List.of());
    }

    /**
     * Creates a new instance with added mappings from a path element's index to a components context key name.
     * This method is a part of little language to configure path elements an query parameters mappings.
     * @param position a position in the path, starting from 0
     * @param key a mapped state key name for the reference in the components context
     * @return a new instance
     */
    public AddressBarSyncComponent withPathElement(int position, String key) {
        final List<PositionKey> l = new ArrayList<>(this.pathElementsKeys);
        l.add(new PositionKey(position, key));
        return new AddressBarSyncComponent(this.initialRelativeUrl, this.subTreeDefinition, l, this.queryParametersNameKeys);
    }

    /**
     * Creates a new instance with added mappings from a query parameter identified by its name to a components context key name.
     * This method is a part of little language to configure path elements an query parameters mappings.
     * @param parameterName a query parameter's name
     * @param key a mapped state key name for the reference in the components context
     * @return a new instance
     */
    public AddressBarSyncComponent withQueryParameter(String parameterName, String key) {
        final List<ParameterNameKey> l = new ArrayList<>(queryParametersNameKeys);
        l.add(new ParameterNameKey(parameterName, key));
        return new AddressBarSyncComponent(this.initialRelativeUrl, this.subTreeDefinition, this.pathElementsKeys, l);
    }


    @Override
    public ComponentStateSupplier<RelativeUrl> initStateSupplier() {
        return (_, _) -> initialRelativeUrl;
    }


    @Override
    public BiFunction<ComponentContext, RelativeUrl, ComponentContext> subComponentsContext() {
        return (componentContext, relativeUrl) -> {
            final Map<String, Object> m = new HashMap<>();
            for (PositionKey pathElementsKey : pathElementsKeys) {
                m.put(pathElementsKey.key, relativeUrl.path().get(pathElementsKey.position));
            }

            for (ParameterNameKey queryParametersNameKey : queryParametersNameKeys) {
                final Optional<String> optionalParameter = relativeUrl.query().parameterValue(queryParametersNameKey.parameterName());
                optionalParameter.ifPresent(p -> {
                    m.put(queryParametersNameKey.key(), p);
                });
            }
            return componentContext.with(m);
        };
    }


    @Override
    public ComponentView<RelativeUrl> componentView() {
        return _ -> _ -> subTreeDefinition;
    }


    @Override
    public ComponentSegment<RelativeUrl> createComponentSegment(final QualifiedSessionId sessionId,
                                                                final TreePositionPath componentPath,
                                                                final RenderContextFactory renderContextFactory,
                                                                final ComponentContext componentContext,
                                                                final Consumer<Command> commandsEnqueue) {
        final ComponentCompositeKey componentId = new ComponentCompositeKey(sessionId, componentType, componentPath);// TODO

        // prepare indices for path elements session keys
        final Map<String, Integer> pathElementsKeysIndices = new HashMap<>();
        for (int i = 0; i < pathElementsKeys.size(); i++) {
            pathElementsKeysIndices.put(pathElementsKeys.get(i).key, pathElementsKeys.get(i).position);
        }

        return new ComponentSegment<>(componentId,
                                      initStateSupplier(),
                                      subComponentsContext(),
                                      componentView(),
                                      new ComponentCallbacks<>(onComponentMountedCallback(),
                                                               onComponentUpdatedCallback(),
                                                               onComponentUnmountedCallback()),
                                      renderContextFactory,
                                      componentContext,
                                      commandsEnqueue) {

            @Override
            protected void onAfterRendered(RelativeUrl state) {
                subscribeForBrowserHistoryEvents();
                subscribeForSessionObjectsUpdates();
            }


            private void subscribeForSessionObjectsUpdates() {
                // subscribe for path elements changes
                for (final PositionKey pathElementKey : pathElementsKeys) {
                    this.addComponentEventHandler(STATE_UPDATED_EVENT_PREFIX + pathElementKey.key,
                                        eventContext -> {
                        final JsonDataType valueJson = eventContext.eventObject().value(STATE_VALUE_ATTRIBUTE_NAME);
                        if (valueJson instanceof JsonDataType.String(String value)) {
                            final int pathElementIndex = pathElementsKeysIndices.get(pathElementKey.key);
                            final RelativeUrl relativeUrl = updatedRelativeUrlForPathElement(getState(), pathElementKey.key, value, pathElementIndex);
                            this.commandsEnqueue.accept(new RemoteCommand.PushHistory(relativeUrl.toString()));
                            setState(relativeUrl);
                        } else {
                            throw new IllegalStateException("Value is missing in a state update event");
                        }

                    },
                                        true);
                }

                // subscribe for query parameters changes
                for (final ParameterNameKey parameterNameKey : queryParametersNameKeys) {
                    this.addComponentEventHandler("stateUpdated." + parameterNameKey.key(),
                                        eventContext -> {
                        final JsonDataType valueJson = eventContext.eventObject().value("value");
                        if (valueJson instanceof JsonDataType.String(String value)) {
                            final RelativeUrl relativeUrl = updatedRelativeUrlForParameter(getState(), parameterNameKey.key(), value);
                            this.commandsEnqueue.accept(new RemoteCommand.PushHistory(relativeUrl.toString()));
                            setState(relativeUrl);
                        } else {
                            throw new IllegalStateException();
                        }
                    },true);
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
                for (int i = 0; i < parameters.size(); i++) {
                    if (parameters.get(i).name().equals(parameterName)) {
                        parameters.set(i, new Query.Parameter(parameterName, parameterValue));
                        return new RelativeUrl(oldRelativeUrl.path(), new Query(parameters), oldRelativeUrl.fragment());
                    }
                }
                parameters.add(new Query.Parameter(parameterName, parameterValue));
                return new RelativeUrl(oldRelativeUrl.path(), new Query(parameters), oldRelativeUrl.fragment());
            }

            private void subscribeForBrowserHistoryEvents() {
                this.addDomEventHandler(PageRendering.WINDOW_DOM_PATH,
                                        HISTORY_ENTRY_CHANGE_EVENT_NAME,
                             eventContext -> {
                                 final RelativeUrl newRelativeUrl = extractRelativeUrl(eventContext.eventObject());
                                 setState(newRelativeUrl);
                             },
                             true,
                              DomEventEntry.NO_MODIFIER);
            }

            private static RelativeUrl extractRelativeUrl(final JsonDataType.Object eventObject) {
                if (eventObject.value("path") instanceof JsonDataType.String(String path)
                    && eventObject.value("query") instanceof JsonDataType.String(String query)
                    && eventObject.value("fragment") instanceof JsonDataType.String(String fragment)) {
                    return new RelativeUrl(Path.of(path), Query.of(query), new Fragment(fragment));
                } else {
                    throw new JsonDataType.JsonException("Error unpacking JSON event object:" + eventObject);
                }
            }

        };
    }

    private record PositionKey(int position, String key) {
    }

    private record ParameterNameKey(String parameterName, String key) {
    }


}
