package rsp.compositions.layout;

/**
 * Default placement behaviour for contracts opened from the same composition,
 * applied when no specific {@link Placement} rule matches.
 * <p>
 * The compatibility default is {@link #ALL_MODAL}, which preserves the
 * framework's historical behaviour: {@code SHOW} opens a layer/modal unless
 * a specific placement rule says otherwise.
 */
public enum GroupPlacementPolicy {
    /**
     * Every contract without a specific rule opens as a modal layer.
     */
    ALL_MODAL,

    /**
     * Every contract without a specific rule replaces the routed primary inline.
     */
    ALL_INLINE,

    /**
     * The scene's first SHOW (when nothing is routed) opens inline; any subsequent
     * SHOW while anything is routed opens modal.
     * <p>
     * Coarse but cheap — needs no group lookup. Useful when the entire scene
     * should behave as a single inline slot.
     */
    FIRST_IN_SCENE_INLINE_OTHERS_MODAL,

    /**
     * The first SHOW within a contract group opens inline; subsequent SHOWs
     * targeting the same group open modal.
     * <p>
     * Specifically: opens inline when the target is bound in a labeled
     * composition group and there is no routed runtime, or when the routed
     * runtime belongs to a different labeled group from the target. Opens
     * modal when the routed runtime is in the same owning group as the target.
     * <p>
     * Targets or routed runtimes that are not bound in the group tree are
     * treated as modal by default, as are contracts owned directly by
     * unlabeled groups. Placement compares group identity, not display labels,
     * so duplicate labels do not collapse separate groups. Use an explicit
     * layout placement rule when an unbound/system contract is intentionally
     * allowed to replace primary content.
     * <p>
     * This is the typical CRUD-app policy: the list view is replaced by an
     * inline form, but a second form opened from inside that form goes modal.
     * Cross-group SHOWs replace the routed primary.
     */
    FIRST_IN_GROUP_INLINE_OTHERS_MODAL
}
