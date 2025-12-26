package rsp.component.definitions;

import rsp.component.*;

import java.util.Map;
import java.util.Objects;

/**
 * A base class for components with a state storage, e.g. in a cache.
 * <p>
 * Specific components extend this class and override
 * {@link rsp.component.definitions.Component#componentView() componentView()} to provide
 * the view implementation.
 * <p>
 * Implementation pattern:
 * <ul>
 *   <li><strong>Base class responsibility:</strong> this class manages state caching
 *      across component mounts/unmounts using a shared state store</li>
 *   <li><strong>Subclass responsibility:</strong> defines the view that renders its UI</li>
 * </ul>
 * <p>
 * <strong>State flow:</strong>
 * <pre>
 * Component mounts
 *   ↓
 * initStateSupplier() checks store for existing state
 *   ↓
 * if found: use stored value | if not found: initialize with provided initial state
 *   ↓
 * Render with ComponentView
 *   ↓
 * User clicks button → state changes
 *   ↓
 * onComponentUpdated() saves state to store
 *   ↓
 * Component unmounts
 *   ↓
 * Component remounts later
 *   ↓
 * State is restored from store (not reinitialized)
 * </pre>
 *
 * @param <S> this component's state type
 */
public abstract class StoredStateComponent<S> extends Component<S> {

    private final S initialState;
    private final Map<ComponentCompositeKey, S> stateStore;

    public StoredStateComponent(final S initialState,
                                final Map<ComponentCompositeKey, S> stateStore) {
        super(StoredStateComponent.class);
        this.initialState = Objects.requireNonNull(initialState);
        this.stateStore = Objects.requireNonNull(stateStore);
    }

    public StoredStateComponent(final Object componentType,
                                final S initialState,
                                final Map<ComponentCompositeKey, S> stateStore) {
        super(componentType);
        this.initialState = Objects.requireNonNull(initialState);
        this.stateStore = Objects.requireNonNull(stateStore);
    }

    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        return   (componentKey, _) -> {
            if (stateStore.containsKey(componentKey)) {
                return stateStore.get(componentKey);
            } else {
                stateStore.put(componentKey, initialState);
                return initialState;
            }
        };
    }

    @Override
    public void onComponentUpdated(final ComponentCompositeKey componentId, final S oldState, final S newState, final StateUpdate<S> stateUpdate) {
        stateStore.put(componentId, newState);
    }

    protected S getInitialState() {
        return this.initialState;
    }

}
