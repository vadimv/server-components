package rsp.compositions.contract;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.EventKey;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.component.StateUpdater;
import rsp.component.definitions.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Base class for intent-driven contracts mounted directly in the component
 * tree. State, subscriptions, and effects belong to this component; its
 * {@code ComponentView} is therefore free to render and dispatch intents only.
 *
 * @param <S> contract-local cache state
 * @param <I> intents accepted from the contract view
 */
public abstract class ContractNodeComponent<S, I> extends Component<S, I> implements Contract {

    private Lookup lookup;
    private final Set<Lookup.Registration> registrations = new HashSet<>();

    protected ContractNodeComponent() {
        super();
    }

    protected ContractNodeComponent(Object componentType) {
        super(componentType);
    }

    @Override
    public final Lookup lookup() {
        if (lookup == null) {
            throw new IllegalStateException("Contract is not mounted");
        }
        return lookup;
    }

    @Override
    public void onMounted(ComponentSegment<S> segment,
                          ComponentCompositeKey componentId,
                          S state,
                          CommandsEnqueue commandsEnqueue,
                          StateUpdater<S> stateUpdate) {
        lookup = LookupFactory.create(segment.contextScope(), segment.componentContext(), commandsEnqueue);
        onContractMounted(state, stateUpdate);
    }

    /**
     * Registers mount-scoped subscriptions after a live lookup is available.
     */
    protected void onContractMounted(S state, StateUpdater<S> stateUpdate) {
    }

    /** Register an event handler for the mounted lifetime of this contract. */
    protected final <T> void subscribe(EventKey<T> key, BiConsumer<String, T> handler) {
        registrations.add(lookup().subscribe(key, handler));
    }

    /** Register a void event handler for the mounted lifetime of this contract. */
    protected final void subscribe(EventKey.VoidKey key, Runnable handler) {
        registrations.add(lookup().subscribe(key, handler));
    }

    /** Observe a context value for the mounted lifetime of this contract. */
    protected final <T> void watch(ContextKey<T> key, BiConsumer<T, T> handler) {
        registrations.add(lookup().watch(key, handler));
    }

    /** Observe a context value for the mounted lifetime of this contract. */
    protected final <T> void watch(ContextKey<T> key, java.util.function.Consumer<T> handler) {
        registrations.add(lookup().watch(key, handler));
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, S state) {
        registrations.forEach(Lookup.Registration::unsubscribe);
        registrations.clear();
        lookup = null;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    @Override
    public boolean isAuthorized(Lookup lookup) {
        return Contract.super.isAuthorized(lookup);
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context;
    }
}
