package rsp.compositions.layout;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.compositions.composition.Group;
import rsp.compositions.contract.Contract;
import rsp.compositions.contract.ContractDescriptor;
import rsp.compositions.contract.ContractNodeComponent;
import rsp.compositions.contract.Scene;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placement rules operate on the direct contract component type hierarchy. */
class PlacementResolverTests {

    @Test
    void exact_rule_wins_over_a_base_component_rule() {
        Map<Class<? extends Contract>, Placement> rules = Map.of(
                BaseContract.class, Placement.MODAL,
                ChildContract.class, Placement.INLINE.primary());

        PlacementDecision decision = PlacementResolver.resolve(
                ChildContract.class, null, rules, GroupPlacementPolicy.ALL_MODAL, null);

        assertTrue(decision.placement().isInline());
        assertEquals(ChildContract.class, decision.matchedContractType());
    }

    @Test
    void closest_ancestor_rule_is_selected() {
        Map<Class<? extends Contract>, Placement> rules = Map.of(
                BaseContract.class, Placement.MODAL,
                ChildContract.class, Placement.INLINE.primary());

        PlacementDecision decision = PlacementResolver.resolve(
                GrandchildContract.class, null, rules, GroupPlacementPolicy.ALL_MODAL, null);

        assertTrue(decision.placement().isInline());
        assertEquals(ChildContract.class, decision.matchedContractType());
    }

    @Test
    void group_policy_provides_the_default_when_no_rule_matches() {
        PlacementDecision allInline = PlacementResolver.resolve(
                BaseContract.class, null, Map.of(), GroupPlacementPolicy.ALL_INLINE, null);
        PlacementDecision allModal = PlacementResolver.resolve(
                BaseContract.class, null, Map.of(), GroupPlacementPolicy.ALL_MODAL, null);

        assertTrue(allInline.placement().isInline());
        assertTrue(allModal.placement().isModal());
        assertNull(allInline.matchedContractType());
    }

    @Test
    void first_in_group_policy_keeps_same_group_targets_modal() {
        Group posts = new Group("Posts")
                .bind(BaseContract.class, BaseContract::new)
                .bind(ChildContract.class, ChildContract::new);
        Scene scene = Scene.of(ContractDescriptor.forContract(BaseContract.class, Map.of()), Map.of(),
                new rsp.compositions.composition.Composition(new rsp.compositions.routing.Router(),
                        new DefaultLayout(), posts));

        PlacementDecision decision = PlacementResolver.resolve(ChildContract.class, scene, Map.of(),
                GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, posts);

        assertTrue(decision.placement().isModal());
    }

    @Test
    void explicit_rule_overrides_group_policy_for_unlabeled_contracts() {
        Group system = new Group().bind(UnrelatedContract.class, UnrelatedContract::new);

        PlacementDecision decision = PlacementResolver.resolve(UnrelatedContract.class, null,
                Map.of(UnrelatedContract.class, Placement.INLINE.primary()),
                GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, system);

        assertTrue(decision.placement().isInline());
        assertEquals(UnrelatedContract.class, decision.matchedContractType());
    }

    static class BaseContract extends ContractNodeComponent<String, Object> {
        @Override public ComponentStateSupplier<String> initStateSupplier() { return (_, _) -> "ready"; }
        @Override public ComponentView<String, Object> componentView() { return _ -> _ -> null; }
        @Override public String title() { return "Base"; }
    }

    static class ChildContract extends BaseContract {}
    static class GrandchildContract extends ChildContract {}
    static class UnrelatedContract extends BaseContract {}
}
