package rsp.compositions;

import java.util.List;

/**
 * Module - Declares a feature domain's contracts and configuration.
 * <p>
 * Each module groups related views and configures the edit/create workflow mode.
 * Action handling is delegated to Contracts (e.g., EditViewContract.save(), delete()).
 */
public interface Module {
    /**
     * View placements for this module (list, edit, detail views).
     */
    List<ViewPlacement> views();

    /**
     * The edit mode for create/edit workflows.
     * <p>
     * Determines how the create form appears:
     * <ul>
     *   <li>{@link EditMode#SEPARATE_PAGE} - Navigate to /entity/new (default)</li>
     *   <li>{@link EditMode#QUERY_PARAM} - Stay on list with ?create=true overlay</li>
     *   <li>{@link EditMode#MODAL} - Pure component state modal, no URL change</li>
     * </ul>
     *
     * @return The edit mode (default: SEPARATE_PAGE)
     */
    default EditMode editMode() {
        return EditMode.SEPARATE_PAGE;
    }

    /**
     * The token used in URLs to indicate create mode.
     * <p>
     * For SEPARATE_PAGE mode: /posts/{createToken} (e.g., /posts/new)
     * For QUERY_PARAM mode: query param name is always "create"
     *
     * @return The create token (default: "new")
     */
    default String createToken() {
        return "new";
    }

    /**
     * Find the EditViewContract class for this module.
     * <p>
     * Used by QUERY_PARAM and MODAL modes to determine which contract
     * to render in the overlay.
     *
     * @return The EditViewContract class, or null if not found
     */
    default Class<? extends EditViewContract<?>> editContractClass() {
        for (ViewPlacement placement : views()) {
            if (EditViewContract.class.isAssignableFrom(placement.contractClass())) {
                @SuppressWarnings("unchecked")
                Class<? extends EditViewContract<?>> editClass =
                        (Class<? extends EditViewContract<?>>) placement.contractClass();
                return editClass;
            }
        }
        return null;
    }
}
