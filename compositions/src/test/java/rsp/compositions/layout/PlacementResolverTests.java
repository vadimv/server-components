package rsp.compositions.layout;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ContextScope;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.contract.ContractRuntime;
import rsp.compositions.contract.LookupFactory;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;
import rsp.compositions.routing.Router;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PlacementResolver}.
 */
public class PlacementResolverTests {

    @Nested
    class ExactRuleBeatsBaseRule {

        @Test
        void child_rule_wins_over_base_rule() {
            final Map<Class<? extends ViewContract>, Placement> placements = new LinkedHashMap<>();
            placements.put(BaseTestContract.class, Placement.MODAL);
            placements.put(ChildTestContract.class, Placement.INLINE.primary());

            final PlacementDecision decision = PlacementResolver.resolve(
                    ChildTestContract.class, null, placements, GroupPlacementPolicy.ALL_MODAL, null);

            assertTrue(decision.placement().isInline());
            assertEquals(ChildTestContract.class, decision.matchedContractType());
            assertEquals(PlacementDecisionSource.LAYOUT_PLACEMENT, decision.source());
        }

        @Test
        void child_rule_wins_regardless_of_insertion_order() {
            final Map<Class<? extends ViewContract>, Placement> placements = new LinkedHashMap<>();
            placements.put(ChildTestContract.class, Placement.INLINE.primary());
            placements.put(BaseTestContract.class, Placement.MODAL);

            final PlacementDecision decision = PlacementResolver.resolve(
                    ChildTestContract.class, null, placements, GroupPlacementPolicy.ALL_MODAL, null);

            assertTrue(decision.placement().isInline());
            assertEquals(ChildTestContract.class, decision.matchedContractType());
        }

        @Test
        void grandchild_uses_closest_ancestor_rule() {
            final Map<Class<? extends ViewContract>, Placement> placements = new LinkedHashMap<>();
            placements.put(BaseTestContract.class, Placement.MODAL);
            placements.put(ChildTestContract.class, Placement.INLINE.primary());

            final PlacementDecision decision = PlacementResolver.resolve(
                    GrandChildTestContract.class, null, placements, GroupPlacementPolicy.ALL_MODAL, null);

            assertTrue(decision.placement().isInline());
            assertEquals(ChildTestContract.class, decision.matchedContractType());
        }

        @Test
        void exact_match_wins_distance_zero() {
            final Map<Class<? extends ViewContract>, Placement> placements = Map.of(
                    BaseTestContract.class, Placement.INLINE.primary());

            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, null, placements, GroupPlacementPolicy.ALL_MODAL, null);

            assertTrue(decision.placement().isInline());
            assertEquals(BaseTestContract.class, decision.matchedContractType());
        }

        @Test
        void unrelated_rule_is_ignored() {
            final Map<Class<? extends ViewContract>, Placement> placements = Map.of(
                    UnrelatedContract.class, Placement.INLINE.primary());

            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, null, placements, GroupPlacementPolicy.ALL_MODAL, null);

            assertTrue(decision.placement().isModal());
            assertEquals(PlacementDecisionSource.GROUP_PLACEMENT_POLICY, decision.source());
            assertNull(decision.matchedContractType());
        }
    }

    @Nested
    class GroupPolicies {

        @Test
        void all_modal_with_no_rule_returns_modal() {
            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, null, Map.of(), GroupPlacementPolicy.ALL_MODAL, null);

            assertTrue(decision.placement().isModal());
            assertEquals(PlacementDecisionSource.GROUP_PLACEMENT_POLICY, decision.source());
            assertNull(decision.matchedContractType());
        }

        @Test
        void all_inline_with_no_rule_returns_inline_primary() {
            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, null, Map.of(), GroupPlacementPolicy.ALL_INLINE, null);

            assertTrue(decision.placement().isInline());
            assertEquals("primary", decision.placement().slot());
            assertEquals(PlacementDecisionSource.GROUP_PLACEMENT_POLICY, decision.source());
        }
    }

    @Nested
    class FirstInSceneInlineOthersModal {

        @Test
        void null_scene_returns_inline() {
            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, null, Map.of(),
                    GroupPlacementPolicy.FIRST_IN_SCENE_INLINE_OTHERS_MODAL, null);

            assertTrue(decision.placement().isInline());
            assertEquals("primary", decision.placement().slot());
        }

        @Test
        void null_routed_runtime_returns_inline() {
            final Scene scene = sceneWithoutRoutedRuntime();

            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, scene, Map.of(),
                    GroupPlacementPolicy.FIRST_IN_SCENE_INLINE_OTHERS_MODAL, null);

            assertTrue(decision.placement().isInline());
        }

        @Test
        void active_routed_runtime_returns_modal_regardless_of_group() {
            final Scene scene = sceneWithRoutedRuntime(BaseTestContract.class);

            final PlacementDecision decision = PlacementResolver.resolve(
                    UnrelatedContract.class, scene, Map.of(),
                    GroupPlacementPolicy.FIRST_IN_SCENE_INLINE_OTHERS_MODAL, null);

            assertTrue(decision.placement().isModal());
            assertEquals(PlacementDecisionSource.GROUP_PLACEMENT_POLICY, decision.source());
        }
    }

    @Nested
    class FirstInGroupInlineOthersModal {

        @Test
        void null_scene_returns_inline() {
            final Group contracts = twoGroupComposition().contracts();

            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, null, Map.of(),
                    GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, contracts);

            assertTrue(decision.placement().isInline());
        }

        @Test
        void null_routed_runtime_returns_inline() {
            final Composition composition = twoGroupComposition();
            final Scene scene = Scene.of(null, Map.of(), Map.of(), composition);

            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, scene, Map.of(),
                    GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, composition.contracts());

            assertTrue(decision.placement().isInline());
        }

        @Test
        void target_in_same_group_as_routed_returns_modal() {
            final Composition composition = twoGroupComposition();
            final Scene scene = Scene.of(
                    newRuntime(BaseTestContract.class, BaseTestContract::new),
                    Map.of(), Map.of(), composition);

            final PlacementDecision decision = PlacementResolver.resolve(
                    ChildTestContract.class, scene, Map.of(),
                    GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, composition.contracts());

            assertTrue(decision.placement().isModal());
            assertEquals(PlacementDecisionSource.GROUP_PLACEMENT_POLICY, decision.source());
        }

        @Test
        void target_in_different_group_from_routed_returns_inline() {
            final Composition composition = twoGroupComposition();
            final Scene scene = Scene.of(
                    newRuntime(BaseTestContract.class, BaseTestContract::new),
                    Map.of(), Map.of(), composition);

            final PlacementDecision decision = PlacementResolver.resolve(
                    UnrelatedContract.class, scene, Map.of(),
                    GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, composition.contracts());

            assertTrue(decision.placement().isInline());
            assertEquals("primary", decision.placement().slot());
        }

        @Test
        void unbound_target_with_bound_routed_returns_inline() {
            // Composition only binds Posts contracts; UnrelatedContract is not in the group tree.
            // Treated as a different group from the routed contract — INLINE.
            final Composition composition = singleGroupComposition();
            final Scene scene = Scene.of(
                    newRuntime(BaseTestContract.class, BaseTestContract::new),
                    Map.of(), Map.of(), composition);

            final PlacementDecision decision = PlacementResolver.resolve(
                    UnrelatedContract.class, scene, Map.of(),
                    GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, composition.contracts());

            assertTrue(decision.placement().isInline());
        }

        @Test
        void null_group_with_active_routed_falls_back_to_modal() {
            final Scene scene = sceneWithRoutedRuntime(BaseTestContract.class);

            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, scene, Map.of(),
                    GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL, null);

            assertTrue(decision.placement().isModal(),
                    "without a group, we cannot tell same-vs-different group; conservative default is modal");
        }
    }

    @Nested
    class ExplicitOverridesGroupPolicy {

        @Test
        void explicit_modal_beats_all_inline_policy() {
            final Map<Class<? extends ViewContract>, Placement> placements = Map.of(
                    BaseTestContract.class, Placement.MODAL);

            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, null, placements, GroupPlacementPolicy.ALL_INLINE, null);

            assertTrue(decision.placement().isModal());
            assertEquals(PlacementDecisionSource.LAYOUT_PLACEMENT, decision.source());
            assertEquals(BaseTestContract.class, decision.matchedContractType());
        }

        @Test
        void explicit_inline_beats_all_modal_policy() {
            final Map<Class<? extends ViewContract>, Placement> placements = Map.of(
                    BaseTestContract.class, Placement.INLINE.primary());

            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, null, placements, GroupPlacementPolicy.ALL_MODAL, null);

            assertTrue(decision.placement().isInline());
            assertEquals(PlacementDecisionSource.LAYOUT_PLACEMENT, decision.source());
        }

        @Test
        void explicit_modal_beats_first_in_scene_when_no_routed() {
            final Map<Class<? extends ViewContract>, Placement> placements = Map.of(
                    BaseTestContract.class, Placement.MODAL);

            final PlacementDecision decision = PlacementResolver.resolve(
                    BaseTestContract.class, null, placements,
                    GroupPlacementPolicy.FIRST_IN_SCENE_INLINE_OTHERS_MODAL, null);

            assertTrue(decision.placement().isModal());
            assertEquals(PlacementDecisionSource.LAYOUT_PLACEMENT, decision.source());
        }
    }

    @Nested
    class FrameworkDefault {

        @Test
        void framework_default_is_modal() {
            final PlacementDecision decision = PlacementDecision.frameworkDefault();

            assertTrue(decision.placement().isModal());
            assertEquals(PlacementDecisionSource.FRAMEWORK_DEFAULT, decision.source());
            assertTrue(decision.userOverridable());
            assertNull(decision.matchedContractType());
        }

        @Test
        void layout_interface_default_returns_framework_default() {
            final Layout layout = (scene, lookup) -> rsp.dsl.Html.div();

            final PlacementDecision decision = layout.resolvePlacement(BaseTestContract.class, null);

            assertEquals(PlacementDecisionSource.FRAMEWORK_DEFAULT, decision.source());
            assertTrue(decision.placement().isModal());
        }
    }

    // --- Scene fixtures -----------------------------------------------------

    private Scene sceneWithoutRoutedRuntime() {
        return Scene.of(null, Map.of(), Map.of(), singleGroupComposition());
    }

    private Scene sceneWithRoutedRuntime(Class<? extends ViewContract> routedClass) {
        final ContractRuntime runtime = newRuntime(routedClass, lookup -> {
            if (routedClass == BaseTestContract.class) return new BaseTestContract(lookup);
            if (routedClass == ChildTestContract.class) return new ChildTestContract(lookup);
            if (routedClass == UnrelatedContract.class) return new UnrelatedContract(lookup);
            throw new IllegalArgumentException("Unknown class: " + routedClass);
        });
        return Scene.of(runtime, Map.of(), Map.of(), singleGroupComposition());
    }

    private Composition singleGroupComposition() {
        final Group group = new Group("Posts")
                .bind(BaseTestContract.class, BaseTestContract::new, () -> null)
                .bind(ChildTestContract.class, ChildTestContract::new, () -> null);
        return new Composition(
                new Router().route("/base", BaseTestContract.class),
                new DefaultLayout(),
                group);
    }

    private Composition twoGroupComposition() {
        // BaseTestContract + ChildTestContract live in "Posts"; UnrelatedContract lives in "Comments".
        final Group posts = new Group("Posts")
                .bind(BaseTestContract.class, BaseTestContract::new, () -> null)
                .bind(ChildTestContract.class, ChildTestContract::new, () -> null);
        final Group comments = new Group("Comments")
                .bind(UnrelatedContract.class, UnrelatedContract::new, () -> null);
        final Group root = new Group()
                .add(posts)
                .add(comments);
        return new Composition(
                new Router().route("/base", BaseTestContract.class),
                new DefaultLayout(),
                root);
    }

    private ContractRuntime newRuntime(Class<? extends ViewContract> cls,
                                       java.util.function.Function<Lookup, ViewContract> factory) {
        final ComponentContext ctx = baseContext();
        final ContextScope.Controller controller = ContextScope.controller(ctx);
        final Lookup lookup = LookupFactory.create(controller.scope(), ctx);
        final ViewContract contract = factory.apply(lookup);
        assertNotNull(contract, "factory must produce a contract");
        return new ContractRuntime(cls, contract, controller);
    }

    private ComponentContext baseContext() {
        return new ComponentContext()
                .with(CommandsEnqueue.class, (CommandsEnqueue) _ -> {})
                .with(Subscriber.class, new NoOpSubscriber());
    }

    // --- Test contract fixtures --------------------------------------------

    static class BaseTestContract extends ViewContract {
        BaseTestContract(Lookup lookup) { super(lookup); }
        @Override public ComponentContext enrichContext(ComponentContext context) { return context; }
        @Override public String title() { return "Base"; }
    }

    static class ChildTestContract extends BaseTestContract {
        ChildTestContract(Lookup lookup) { super(lookup); }
    }

    static class GrandChildTestContract extends ChildTestContract {
        GrandChildTestContract(Lookup lookup) { super(lookup); }
    }

    static class UnrelatedContract extends ViewContract {
        UnrelatedContract(Lookup lookup) { super(lookup); }
        @Override public ComponentContext enrichContext(ComponentContext context) { return context; }
        @Override public String title() { return "Unrelated"; }
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType,
                                          Consumer<EventContext> eventHandler,
                                          boolean preventDefault,
                                          DomEventEntry.Modifier modifier) {}

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            return () -> {};
        }
    }
}
