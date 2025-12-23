package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.component.TreeBuilderFactory;
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

import static rsp.component.definitions.ContextStateComponent.STATE_UPDATED_EVENT_PREFIX;
import static rsp.page.PageBuilder.WINDOW_DOM_PATH;


/**
 * This wrapper component synchronizes the current browser page address bar's path elements and query parameters
 * to the wrapped components subtree states.
 * On an initial rendering, the actual URL path and query parameters state are mapped to the context's fields to be propagated with the components' context to  its subtree components.
 * Later when the bind state changes somewhere in the components down the wrapped subtree, this component is notified and updates the address bar.
 * <p>
 * <strong>State flow:</strong>
 * <pre>
 * AddressBarSyncComponent (parent in tree) sets context attributes
 *   ↓
 * ContextStateComponent reads attribute value and parses it to state
 *   ↓
 * ContextCounterComponent.componentView() provides the view
 *   ↓
 * ComponentView renders the counter UI
 *   ↓
 * On event: state changes in a bind component down the tree and propagates back up to AddressBarSyncComponent
 *   ↓
 * AddressBarSyncComponent re-renders its subtree and updates URL and browser history
 * </pre>
 *
 * @see ContextStateComponent for state-to-context synchronization
 */
public abstract class AddressBarSyncComponent extends Component<RelativeUrl> {

    /**
     * A browser session's history entry change event name.
     */
    private static final String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";

    private final RelativeUrl initialRelativeUrl;

    public AddressBarSyncComponent(final RelativeUrl initialRelativeUrl) {
        super(AddressBarSyncComponent.class);
        this.initialRelativeUrl = Objects.requireNonNull(initialRelativeUrl);
    }

    public abstract List<PositionKey> pathElementsPositionKeys();


    public abstract List<ParameterNameKey> queryParametersNamedKeys();

    @Override
    public ComponentStateSupplier<RelativeUrl> initStateSupplier() {
        return (_, _) -> initialRelativeUrl;
    }


    @Override
    public BiFunction<ComponentContext, RelativeUrl, ComponentContext> subComponentsContext() {
        return (componentContext, relativeUrl) -> {
            Objects.requireNonNull(componentContext);
            Objects.requireNonNull(relativeUrl);
            final Map<String, ContextStateComponent.ContextValue> m = new HashMap<>();
            // add URL's path elements for configured positions
            for (PositionKey pathElementsKey : pathElementsPositionKeys()) {
                final String contextValue = relativeUrl.path().get(pathElementsKey.position);
                m.put(pathElementsKey.key, contextValue == null ?
                        new ContextStateComponent.ContextValue.Empty() : new ContextStateComponent.ContextValue.StringValue(contextValue));
            }
            // add query parameters for configured parameters names
            for (ParameterNameKey queryParametersNameKey : queryParametersNamedKeys()) {
                final String parameterValue = relativeUrl.query().parameterValue(queryParametersNameKey.parameterName());
                    m.put(queryParametersNameKey.key(), parameterValue == null ?
                            new ContextStateComponent.ContextValue.Empty() : new ContextStateComponent.ContextValue.StringValue(parameterValue));
            }
            return componentContext.with(m);
        };
    }


    @Override
    public ComponentSegment<RelativeUrl> createComponentSegment(final QualifiedSessionId sessionId,
                                                                final TreePositionPath componentPath,
                                                                final TreeBuilderFactory treeBuilderFactory,
                                                                final ComponentContext componentContext,
                                                                final Consumer<Command> commandsEnqueue) {
        super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
        final ComponentCompositeKey componentId = new ComponentCompositeKey(sessionId, componentType, componentPath);// TODO should it be a method for that?

        // prepare indices for path elements session keys
        final Map<String, Integer> pathElementsKeysIndices = new HashMap<>();
        for (final PositionKey pathElementsKey : pathElementsPositionKeys()) {
            pathElementsKeysIndices.put(pathElementsKey.key, pathElementsKey.position);
        }

        return new ComponentSegment<>(componentId,
                                      initStateSupplier(),
                                      subComponentsContext(),
                                      componentView(),
                                      this,
                                      treeBuilderFactory,
                                      componentContext,
                                      commandsEnqueue) {

            @Override
            protected void onAfterRendered(final RelativeUrl state) {
                subscribeForBrowserHistoryEvents();
                subscribeForSessionObjectsUpdates();
            }

            private void subscribeForSessionObjectsUpdates() {
                // subscribe for path elements changes
                for (final PositionKey pathElementKey : pathElementsPositionKeys()) {
                    this.addComponentEventHandler(STATE_UPDATED_EVENT_PREFIX + pathElementKey.key,
                                        eventContext -> {
                        final Object valueObject = eventContext.eventObject();
                        if (valueObject instanceof ContextStateComponent.ContextValue.StringValue(String value)) {
                            final int pathElementIndex = pathElementsKeysIndices.get(pathElementKey.key);
                            final RelativeUrl relativeUrl = updatedRelativeUrlForPathElement(getState(), pathElementKey.key, value, pathElementIndex);
                            this.commandsEnqueue.accept(new RemoteCommand.PushHistory(relativeUrl.toString()));
                            setState(relativeUrl);
                        }
                    },
                                        true);
                }

                // subscribe for query parameters changes
                for (final ParameterNameKey parameterNameKey : queryParametersNamedKeys()) {
                    this.addComponentEventHandler("stateUpdated." + parameterNameKey.key(),
                                        eventContext -> {
                        final Object valueObject = eventContext.eventObject();
                        if (valueObject instanceof ContextStateComponent.ContextValue.StringValue(String value)) {
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
                this.addDomEventHandler(WINDOW_DOM_PATH,
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

    /**
     * Creates a new instance with added mappings from a path element's index to a components context key name.
     * This method is a part of little language to configure path elements query parameters mappings.
     * @param position a position in the path, starting from 0
     * @param key a mapped state key name for the reference in the components context
     */
    public record PositionKey(int position, String key) {
        public PositionKey {
            Objects.requireNonNull(key);
        }
    }

    /**
     * Creates a new instance with added mappings from a query parameter identified by its name to a components context key name.
     * This method is a part of little language to configure path elements query parameters mappings.
     * @param parameterName a query parameter's name
     * @param key a mapped state key name for the reference in the components context
     */
    public record ParameterNameKey(String parameterName, String key) {
        public ParameterNameKey {
            Objects.requireNonNull(parameterName);
            Objects.requireNonNull(key);
        }
    }


}
