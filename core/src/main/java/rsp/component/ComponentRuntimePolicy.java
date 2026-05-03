package rsp.component;

/**
 * Declarative runtime policy for a component segment.
 * <p>
 * Unlike {@link ComponentCallbacks}, these methods are not lifecycle hooks. The
 * framework asks them while deciding how a component participates in runtime
 * behavior such as subscriber boundaries and reconciliation.
 * <p>
 * Component subclasses usually inherit this interface through
 * {@link rsp.component.definitions.Component}. Override these methods when the
 * component needs non-default runtime behavior.
 */
public interface ComponentRuntimePolicy {

    ComponentRuntimePolicy DEFAULT = new ComponentRuntimePolicy() {};

    /**
     * Whether descendants should receive this component's subscriber in context.
     * <p>
     * Most components form an event boundary and use their own subscriber.
     * Transparent framework components can return {@code false} to preserve the
     * upstream subscriber for their children.
     */
    default boolean providesSubscriberBoundary() {
        return true;
    }

    /**
     * Whether this component segment can be reused when its parent re-renders
     * and produces a component with the same positional identity.
     * <p>
     * Reusable segments preserve their state and mounted lifecycle while their
     * upstream {@link ComponentContext} is replaced. Context watchers registered
     * through a {@link ContextScope}-backed {@link ContextLookup} are notified for
     * changed keys, and {@link ComponentCallbacks#onAfterRendered} runs again so
     * render-time component subscriptions can be refreshed.
     * <p>
     * The default is {@code false}. Return {@code true} only for components whose
     * state is independent of one-time context snapshots, or whose context
     * dependencies are handled through {@link ContextScope} and
     * {@code watch(...)}. Keep the default for components that:
     * <ul>
     *     <li>derive essential state from mount-time context and do not watch it,</li>
     *     <li>store a fixed/snapshot {@link ContextLookup} for later reads,</li>
     *     <li>own imperative resources that must be recreated when parent context changes,</li>
     *     <li>appear as repeated same-type children in dynamic lists without explicit keys.</li>
     * </ul>
     */
    default boolean isReusable() {
        return false;
    }
}
