package rsp.compositions.contract;

import rsp.compositions.composition.Composition;
import rsp.compositions.composition.UiRegistry;

import java.util.Map;

/**
 * Scene - Immutable snapshot of a rendered view's complete contract setup.
 * <p>
 * A Scene represents everything needed to render a view:
 * <ul>
 *   <li>Primary contract instance (fully instantiated, authorized, handlers registered)</li>
 *   <li>Composition reference</li>
 *   <li>Overlay contracts (pre-instantiated for Slot.OVERLAY placements)</li>
 *   <li>UiRegistry for resolving contracts to UI components</li>
 *   <li>Authorization state</li>
 *   <li>Build metadata (timestamp, any errors)</li>
 *   <li>Auto-open overlay (when OVERLAY contract is routed directly)</li>
 * </ul>
 * <p>
 * Benefits of storing scene in component state:
 * <ul>
 *   <li>Contracts instantiated once at mount, not every render</li>
 *   <li>Enables caching by route pattern</li>
 *   <li>Explicit error handling via {@link #isValid()}</li>
 *   <li>Testable - can create Scene directly without full context</li>
 * </ul>
 *
 * @param primaryContract The main ViewContract for this route (fully instantiated)
 * @param composition The Composition containing the contract
 * @param overlayContracts Pre-instantiated contracts for Slot.OVERLAY placements (keyed by contract class)
 * @param uiRegistry Registry for resolving contracts to UI components
 * @param authorized Whether user is authorized for this contract
 * @param timestamp When the scene was built (for debugging/caching)
 * @param error If scene building failed, this contains the exception (other fields may be null)
 * @param autoOpenOverlay Overlay to auto-activate (when OVERLAY contract routed via URL), null otherwise
 * @param overlayRoutePattern The route pattern for URL-synced overlays (for restoring URL on close), null otherwise
 */
public record Scene(
    ViewContract primaryContract,
    rsp.compositions.composition.Composition composition,
    Map<Class<? extends ViewContract>, ViewContract> overlayContracts,
    UiRegistry uiRegistry,
    boolean authorized,
    long timestamp,
    Exception error,
    Class<? extends ViewContract> autoOpenOverlay,
    String overlayRoutePattern
) {
    /**
     * Check if the scene is valid and ready for rendering.
     *
     * @return true if no error occurred, contract exists, and user is authorized
     */
    public boolean isValid() {
        return error == null && primaryContract != null && authorized;
    }

    /**
     * Get an overlay contract by its class.
     *
     * @param contractClass The contract class to look up
     * @return The overlay contract, or null if not found
     */
    public ViewContract overlayContract(Class<? extends ViewContract> contractClass) {
        return overlayContracts != null ? overlayContracts.get(contractClass) : null;
    }

    /**
     * Check if this scene has any overlay contracts.
     *
     * @return true if there are overlay contracts
     */
    public boolean hasOverlays() {
        return overlayContracts != null && !overlayContracts.isEmpty();
    }

    /**
     * Create a valid scene with primary contract, composition, and UI registry (no overlays).
     */
    public static Scene of(ViewContract primaryContract, Composition composition, UiRegistry uiRegistry) {
        return new Scene(primaryContract, composition, Map.of(), uiRegistry, true, System.currentTimeMillis(), null, null, null);
    }

    /**
     * Create a valid scene with primary contract, composition, overlay contracts, and UI registry.
     */
    public static Scene of(ViewContract primaryContract, Composition composition,
                           Map<Class<? extends ViewContract>, ViewContract> overlayContracts,
                           UiRegistry uiRegistry) {
        return new Scene(primaryContract, composition,
                overlayContracts != null ? overlayContracts : Map.of(),
                uiRegistry, true, System.currentTimeMillis(), null, null, null);
    }

    /**
     * Create a valid scene with auto-open overlay (for OVERLAY contracts routed via URL).
     *
     * @param primaryContract The primary contract (parent route's contract)
     * @param composition The composition
     * @param overlayContracts Pre-instantiated overlay contracts
     * @param uiRegistry The UI registry
     * @param autoOpenOverlay The overlay class to auto-activate
     * @param overlayRoutePattern The route pattern for URL sync (e.g., "/posts/:id")
     */
    public static Scene withAutoOpenOverlay(ViewContract primaryContract, Composition composition,
                                            Map<Class<? extends ViewContract>, ViewContract> overlayContracts,
                                            UiRegistry uiRegistry,
                                            Class<? extends ViewContract> autoOpenOverlay,
                                            String overlayRoutePattern) {
        return new Scene(primaryContract, composition,
                overlayContracts != null ? overlayContracts : Map.of(),
                uiRegistry, true, System.currentTimeMillis(), null, autoOpenOverlay, overlayRoutePattern);
    }

    /**
     * Create an unauthorized scene.
     */
    public static Scene unauthorized(ViewContract contract, Composition composition, UiRegistry uiRegistry) {
        return new Scene(contract, composition, Map.of(), uiRegistry, false, System.currentTimeMillis(), null, null, null);
    }

    /**
     * Create an error scene.
     */
    public static Scene error(Exception e) {
        return new Scene(null, null, Map.of(), null, false, System.currentTimeMillis(), e, null, null);
    }

    /**
     * Check if this scene has an overlay that should auto-open.
     *
     * @return true if there's an overlay to auto-activate
     */
    public boolean hasAutoOpenOverlay() {
        return autoOpenOverlay != null;
    }
}
