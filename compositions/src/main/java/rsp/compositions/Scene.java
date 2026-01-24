package rsp.compositions;

import java.util.Map;

/**
 * Scene - Immutable snapshot of a rendered view's complete contract setup.
 * <p>
 * A Scene represents everything needed to render a view:
 * <ul>
 *   <li>Primary contract instance (fully instantiated, authorized, handlers registered)</li>
 *   <li>Module reference</li>
 *   <li>Overlay contracts (pre-instantiated for Slot.OVERLAY placements)</li>
 *   <li>Authorization state</li>
 *   <li>Build metadata (timestamp, any errors)</li>
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
 * @param module The Module containing the contract
 * @param overlayContracts Pre-instantiated contracts for Slot.OVERLAY placements (keyed by contract class)
 * @param authorized Whether user is authorized for this contract
 * @param timestamp When the scene was built (for debugging/caching)
 * @param error If scene building failed, this contains the exception (other fields may be null)
 */
public record Scene(
    ViewContract primaryContract,
    Module module,
    Map<Class<? extends ViewContract>, ViewContract> overlayContracts,
    boolean authorized,
    long timestamp,
    Exception error
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
     * Create a valid scene with primary contract and module (no overlays).
     */
    public static Scene of(ViewContract primaryContract, Module module) {
        return new Scene(primaryContract, module, Map.of(), true, System.currentTimeMillis(), null);
    }

    /**
     * Create a valid scene with primary contract, module, and overlay contracts.
     */
    public static Scene of(ViewContract primaryContract, Module module,
                           Map<Class<? extends ViewContract>, ViewContract> overlayContracts) {
        return new Scene(primaryContract, module,
                overlayContracts != null ? overlayContracts : Map.of(),
                true, System.currentTimeMillis(), null);
    }

    /**
     * Create an unauthorized scene.
     */
    public static Scene unauthorized(ViewContract contract, Module module) {
        return new Scene(contract, module, Map.of(), false, System.currentTimeMillis(), null);
    }

    /**
     * Create an error scene.
     */
    public static Scene error(Exception e) {
        return new Scene(null, null, Map.of(), false, System.currentTimeMillis(), e);
    }
}
