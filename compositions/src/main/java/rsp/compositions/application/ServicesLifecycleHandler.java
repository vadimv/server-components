package rsp.compositions.application;

import rsp.component.Lookup;

/**
 * Opt-in lifecycle hooks for services.
 * <p>
 * Services that implement this interface receive callbacks at scope boundaries:
 * <ul>
 *   <li>App-level: {@code onStart()} when a session starts, {@code onStop()} when it ends</li>
 *   <li>Composition-level: {@code onStart()} when a scene is built, {@code onStop()} on navigation away</li>
 * </ul>
 * <p>
 * Services that don't implement this interface are unaffected — the {@code instanceof} check is a no-op.
 */
public interface ServicesLifecycleHandler {
    default void onStart(Lookup lookup) {}
    default void onStop() {}
}
