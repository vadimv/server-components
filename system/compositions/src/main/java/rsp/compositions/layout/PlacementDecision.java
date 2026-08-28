package rsp.compositions.layout;

import rsp.compositions.block.Block;

import rsp.compositions.block.BlockRuntime;

import java.util.Objects;

/**
 * Effective placement decision for a block.
 *
 * @param placement the chosen placement
 * @param userOverridable whether future user preferences may override it
 * @param source where the decision came from
 * @param matchedBlockType the block type/rule that matched, if any
 */
public record PlacementDecision(Placement placement,
                                boolean userOverridable,
                                PlacementDecisionSource source,
                                Class<? extends BlockRuntime> matchedBlockType) {
    public PlacementDecision {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(source, "source");
    }

    public static PlacementDecision frameworkDefault() {
        return new PlacementDecision(
                Placement.MODAL,
                true,
                PlacementDecisionSource.FRAMEWORK_DEFAULT,
                null);
    }

    public static PlacementDecision layoutPlacement(Placement placement,
                                                    Class<? extends BlockRuntime> matchedBlockType) {
        return new PlacementDecision(
                placement,
                true,
                PlacementDecisionSource.LAYOUT_PLACEMENT,
                matchedBlockType);
    }

    public static PlacementDecision groupPolicy(Placement placement) {
        return new PlacementDecision(
                placement,
                true,
                PlacementDecisionSource.GROUP_PLACEMENT_POLICY,
                null);
    }
}
