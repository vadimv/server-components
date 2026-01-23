package rsp.compositions;

/**
 * Scene - Immutable snapshot of a rendered view's complete contract setup.
 * <p>
 * A Scene represents everything needed to render a view:
 * <ul>
 *   <li>Primary contract instance (fully instantiated, authorized, handlers registered)</li>
 *   <li>Module configuration (editMode, createToken)</li>
 *   <li>Modal overlay contract for MODAL mode (pre-instantiated)</li>
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
 * @param module The Module containing the contract (provides editMode, createToken)
 * @param modalContract Pre-instantiated contract for MODAL mode overlay (nullable)
 * @param authorized Whether user is authorized for this contract
 * @param timestamp When the scene was built (for debugging/caching)
 * @param error If scene building failed, this contains the exception (other fields may be null)
 */
public record Scene(
    ViewContract primaryContract,
    Module module,
    ViewContract modalContract,
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
     * Create a valid scene with primary contract and module.
     */
    public static Scene of(ViewContract primaryContract, Module module) {
        return new Scene(primaryContract, module, null, true, System.currentTimeMillis(), null);
    }

    /**
     * Create a valid scene with primary contract, module, and modal overlay.
     */
    public static Scene of(ViewContract primaryContract, Module module, ViewContract modalContract) {
        return new Scene(primaryContract, module, modalContract, true, System.currentTimeMillis(), null);
    }

    /**
     * Create an unauthorized scene.
     */
    public static Scene unauthorized(ViewContract contract, Module module) {
        return new Scene(contract, module, null, false, System.currentTimeMillis(), null);
    }

    /**
     * Create an error scene.
     */
    public static Scene error(Exception e) {
        return new Scene(null, null, null, false, System.currentTimeMillis(), e);
    }
}
