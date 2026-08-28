package rsp.compositions.layout;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.compositions.composition.Group;
import rsp.compositions.block.BlockRuntime;
import rsp.compositions.block.BlockDescriptor;
import rsp.compositions.block.Block;
import rsp.compositions.block.Scene;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placement rules operate on the direct block component type hierarchy. */
class PlacementResolverTests {

    @Test
    void exact_rule_wins_over_a_base_component_rule() {
        Map<Class<? extends Block<?, ?>>, Placement> rules = Map.of(
                BaseBlock.class, Placement.MODAL,
                ChildBlock.class, Placement.INLINE.primary());

        PlacementDecision decision = PlacementResolver.resolve(
                ChildBlock.class, null, rules, GroupPlacementPolicy.ALL_MODAL, null);

        assertTrue(decision.placement().isInline());
        assertEquals(ChildBlock.class, decision.matchedBlockType());
    }

    @Test
    void closest_ancestor_rule_is_selected() {
        Map<Class<? extends Block<?, ?>>, Placement> rules = Map.of(
                BaseBlock.class, Placement.MODAL,
                ChildBlock.class, Placement.INLINE.primary());

        PlacementDecision decision = PlacementResolver.resolve(
                GrandchildBlock.class, null, rules, GroupPlacementPolicy.ALL_MODAL, null);

        assertTrue(decision.placement().isInline());
        assertEquals(ChildBlock.class, decision.matchedBlockType());
    }

    @Test
    void group_policy_provides_the_default_when_no_rule_matches() {
        PlacementDecision allInline = PlacementResolver.resolve(
                BaseBlock.class, null, Map.of(), GroupPlacementPolicy.ALL_INLINE, null);
        PlacementDecision allModal = PlacementResolver.resolve(
                BaseBlock.class, null, Map.of(), GroupPlacementPolicy.ALL_MODAL, null);

        assertTrue(allInline.placement().isInline());
        assertTrue(allModal.placement().isModal());
        assertNull(allInline.matchedBlockType());
    }

    @Test
    void first_in_group_policy_keeps_same_group_targets_modal() {
        Group posts = new Group("Posts")
                .bind(BaseBlock.class, BaseBlock::new)
                .bind(ChildBlock.class, ChildBlock::new);
        Scene scene = Scene.of(BlockDescriptor.forBlock(BaseBlock.class, Map.of()), Map.of(),
                new rsp.compositions.composition.Composition(new rsp.compositions.routing.Router(),
                        new DefaultLayout(), posts));

        PlacementDecision decision = PlacementResolver.resolve(ChildBlock.class, scene, Map.of(),
                GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, posts);

        assertTrue(decision.placement().isModal());
    }

    @Test
    void explicit_rule_overrides_group_policy_for_unlabeled_blocks() {
        Group system = new Group().bind(UnrelatedBlock.class, UnrelatedBlock::new);

        PlacementDecision decision = PlacementResolver.resolve(UnrelatedBlock.class, null,
                Map.of(UnrelatedBlock.class, Placement.INLINE.primary()),
                GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, system);

        assertTrue(decision.placement().isInline());
        assertEquals(UnrelatedBlock.class, decision.matchedBlockType());
    }

    static class BaseBlock extends Block<String, Object> {
        @Override public ComponentStateSupplier<String> initStateSupplier() { return (_, _) -> "ready"; }
        @Override public ComponentView<String, Object> componentView() { return _ -> _ -> null; }
        @Override public String title() { return "Base"; }
    }

    static class ChildBlock extends BaseBlock {}
    static class GrandchildBlock extends ChildBlock {}
    static class UnrelatedBlock extends BaseBlock {}
}
