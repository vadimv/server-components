package rsp.compositions.layout;

/**
 * Default placement behavior for contracts opened from the same composition.
 * <p>
 * The compatibility default is {@link #ALL_MODAL}, which preserves the
 * framework's current behavior: {@code SHOW} opens a layer/modal unless a
 * specific placement rule says otherwise.
 */
public enum GroupPlacementPolicy {
    ALL_MODAL,
    ALL_INLINE,
    FIRST_INLINE_OTHER_MODAL
}
