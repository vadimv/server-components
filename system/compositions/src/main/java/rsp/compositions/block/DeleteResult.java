package rsp.compositions.block;

import java.util.HashSet;
import java.util.Set;

/**
 * Outcome of a list deletion, including partial success.
 *
 * @param deletedIds IDs that were deleted
 * @param failedIds requested IDs that could not be deleted
 */
public record DeleteResult(Set<String> deletedIds, Set<String> failedIds) {
    public DeleteResult {
        deletedIds = deletedIds == null ? Set.of() : Set.copyOf(deletedIds);
        failedIds = failedIds == null ? Set.of() : Set.copyOf(failedIds);
        Set<String> overlap = new HashSet<>(deletedIds);
        overlap.retainAll(failedIds);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("deletedIds and failedIds must be disjoint");
        }
    }

    /** Create a successful result for all supplied IDs. */
    public static DeleteResult allDeleted(Set<String> ids) {
        return new DeleteResult(ids, Set.of());
    }
}
