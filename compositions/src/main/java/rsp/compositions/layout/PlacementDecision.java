package rsp.compositions.layout;

import rsp.compositions.contract.ViewContract;

import java.util.Objects;

/**
 * Effective placement decision for a contract.
 *
 * @param placement the chosen placement
 * @param userOverridable whether future user preferences may override it
 * @param source where the decision came from
 * @param matchedContractType the contract type/rule that matched, if any
 */
public record PlacementDecision(Placement placement,
                                boolean userOverridable,
                                PlacementDecisionSource source,
                                Class<? extends ViewContract> matchedContractType) {
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
                                                    Class<? extends ViewContract> matchedContractType) {
        return new PlacementDecision(
                placement,
                true,
                PlacementDecisionSource.LAYOUT_PLACEMENT,
                matchedContractType);
    }

    public static PlacementDecision groupPolicy(Placement placement) {
        return new PlacementDecision(
                placement,
                true,
                PlacementDecisionSource.GROUP_PLACEMENT_POLICY,
                null);
    }
}
