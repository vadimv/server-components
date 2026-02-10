package rsp.component.definitions;

import rsp.component.*;
import rsp.dom.DomEventEntry;
import rsp.page.events.RemoteCommand;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.function.BiFunction;

import static rsp.component.definitions.ContextStateComponent.STATE_UPDATED_EVENT_PREFIX;


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
            ComponentContext enrichedContext = componentContext;

            // add URL's path elements for configured positions
            for (PositionKey pathElementsKey : pathElementsPositionKeys()) {
                final String contextValue = relativeUrl.path().get(pathElementsKey.position);
                final ContextStateComponent.ContextValue value = contextValue == null ?
                        new ContextStateComponent.ContextValue.Empty() : new ContextStateComponent.ContextValue.StringValue(contextValue);
                enrichedContext = enrichedContext.with(
                    new ContextKey.StringKey<>(pathElementsKey.key, ContextStateComponent.ContextValue.class),
                    value
                );
            }
            // add query parameters for configured parameters names
            for (ParameterNameKey queryParametersNameKey : queryParametersNamedKeys()) {
                final String parameterValue = relativeUrl.query().parameterValue(queryParametersNameKey.parameterName());
                final ContextStateComponent.ContextValue value = parameterValue == null ?
                        new ContextStateComponent.ContextValue.Empty() : new ContextStateComponent.ContextValue.StringValue(parameterValue);
                enrichedContext = enrichedContext.with(
                    new ContextKey.StringKey<>(queryParametersNameKey.key(), ContextStateComponent.ContextValue.class),
                    value
                );
            }
            return enrichedContext;
        };
    }

    @Override
    public void onAfterRendered(RelativeUrl state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<RelativeUrl> stateUpdate) {
        subscribeForBrowserHistoryEvents(subscriber, stateUpdate);
        subscribeForSessionObjectsUpdates(subscriber, commandsEnqueue, stateUpdate);
    }

    private void subscribeForBrowserHistoryEvents(Subscriber subscriber,
                                                  StateUpdate<RelativeUrl> stateUpdate) {
        subscriber.addWindowEventHandler(HISTORY_ENTRY_CHANGE_EVENT_NAME,
            eventContext -> {
                final RelativeUrl newRelativeUrl = extractRelativeUrl(eventContext.eventObject());
                stateUpdate.setState(newRelativeUrl);
            },
            true,
            DomEventEntry.NO_MODIFIER);
    }

    private void subscribeForSessionObjectsUpdates(Subscriber subscriber,
                                                   CommandsEnqueue commandsEnqueue,
                                                   StateUpdate<RelativeUrl> stateUpdate) {
        // prepare indices for path elements session keys
        final Map<String, Integer> pathElementsKeysIndices = new HashMap<>();
        for (final PositionKey pathElementsKey : pathElementsPositionKeys()) {
            pathElementsKeysIndices.put(pathElementsKey.key, pathElementsKey.position);
        }

        // subscribe for path elements changes
        for (final PositionKey pathElementKey : pathElementsPositionKeys()) {
            subscriber.addComponentEventHandler(STATE_UPDATED_EVENT_PREFIX + pathElementKey.key,
                eventContext -> {
                    final Object valueObject = eventContext.eventObject();
                    if (valueObject instanceof ContextStateComponent.ContextValue.StringValue(String value)) {
                        final int pathElementIndex = pathElementsKeysIndices.get(pathElementKey.key);
                        stateUpdate.applyStateTransformation(currentState -> {
                            final RelativeUrl relativeUrl = updatedRelativeUrlForPathElement(currentState, value, pathElementIndex);
                            commandsEnqueue.offer(new RemoteCommand.PushHistory(relativeUrl.toString()));
                            return relativeUrl;
                        });
                    }
                },
                true);
        }

        // subscribe for query parameters changes
        for (final ParameterNameKey parameterNameKey : queryParametersNamedKeys()) {
            subscriber.addComponentEventHandler(STATE_UPDATED_EVENT_PREFIX + parameterNameKey.key(),
                eventContext -> {
                    final Object valueObject = eventContext.eventObject();
                    if (valueObject instanceof ContextStateComponent.ContextValue.StringValue(String value)) {
                        stateUpdate.applyStateTransformation(currentState -> {
                            final RelativeUrl relativeUrl = updatedRelativeUrlForParameter(currentState, parameterNameKey.parameterName(), value);
                            commandsEnqueue.offer(new RemoteCommand.PushHistory(relativeUrl.toString()));
                            return relativeUrl;
                        });
                    } else {
                        throw new IllegalStateException();
                    }
                },
                true);
        }
    }

    private static RelativeUrl updatedRelativeUrlForPathElement(RelativeUrl oldRelativeUrl,
                                                                String value,
                                                                int pathElementIndex) {
        final List<String> pathElements = new ArrayList<>();
        for (int j = 0; j < oldRelativeUrl.path().elementsCount(); j++) {
            pathElements.add(oldRelativeUrl.path().get(j));
        }
        pathElements.set(pathElementIndex, value);
        final Path newPath = new Path(true, pathElements.toArray(new String[0]));
        return new RelativeUrl(newPath, oldRelativeUrl.query(), oldRelativeUrl.fragment());
    }

    private static RelativeUrl updatedRelativeUrlForParameter(RelativeUrl oldRelativeUrl,
                                                              String parameterName,
                                                              String parameterValue) {
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

    private static RelativeUrl extractRelativeUrl(final JsonDataType.Object eventObject) {
        if (eventObject.value("path") instanceof JsonDataType.String(String path)
            && eventObject.value("query") instanceof JsonDataType.String(String query)
            && eventObject.value("fragment") instanceof JsonDataType.String(String fragment)) {
            return new RelativeUrl(Path.of(path), Query.of(query), new Fragment(fragment));
        } else {
            throw new JsonDataType.JsonException("Error unpacking JSON event object:" + eventObject);
        }
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
