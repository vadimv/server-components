package rsp.compositions.layout;

import rsp.compositions.block.Block;

import rsp.compositions.composition.Group;
import rsp.compositions.block.Scene;
import rsp.compositions.block.BlockRuntime;
import rsp.compositions.block.BlockTarget;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Resolves layout placement hints into effective decisions.
 */
public final class PlacementResolver {
    private PlacementResolver() {}

    public static PlacementDecision resolve(
            Class<? extends Block<?, ?>> blockClass,
            Scene scene,
            Map<? extends Class<? extends BlockRuntime>, Placement> placements,
            GroupPlacementPolicy groupPlacementPolicy,
            Group blocks) {
        return resolve(new BlockTarget(blockClass, blockClass), scene, placements,
                groupPlacementPolicy, blocks);
    }

    public static PlacementDecision resolve(
            BlockTarget target,
            Scene scene,
            Map<? extends Class<? extends BlockRuntime>, Placement> placements,
            GroupPlacementPolicy groupPlacementPolicy,
            Group blocks) {
        Objects.requireNonNull(target, "target");
        Class<? extends Block<?, ?>> blockClass = target.blockClass();
        Objects.requireNonNull(blockClass, "blockClass");
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(groupPlacementPolicy, "groupPlacementPolicy");

        RuleMatch match = findBestRule(blockClass, placements);
        if (match != null) {
            return PlacementDecision.layoutPlacement(match.placement(), match.blockType());
        }

        return switch (groupPlacementPolicy) {
            case ALL_INLINE -> PlacementDecision.groupPolicy(Placement.INLINE.primary());
            case FIRST_IN_SCENE_INLINE_OTHERS_MODAL -> firstInScene(scene);
            case FIRST_IN_GROUP_INLINE_OTHERS_MODAL -> firstInGroup(target, scene, blocks);
            case ALL_MODAL -> PlacementDecision.groupPolicy(Placement.MODAL);
        };
    }

    private static PlacementDecision firstInScene(Scene scene) {
        if (scene == null || scene.routedDescriptor() == null) {
            return PlacementDecision.groupPolicy(Placement.INLINE.primary());
        }
        return PlacementDecision.groupPolicy(Placement.MODAL);
    }

    /**
     * Group-aware inline policy is intentionally conservative for unknown
     * blocks. Only targets that can be found in a labeled composition group
     * are eligible for inferred inline placement; system overlays and other
     * unbound or unlabeled targets stay modal unless an explicit layout rule
     * matched first.
     */
    private static PlacementDecision firstInGroup(BlockTarget target,
                                                  Scene scene,
                                                  Group blocks) {
        if (blocks == null) {
            return PlacementDecision.groupPolicy(Placement.MODAL);
        }
        Optional<Group> targetGroup = blocks.placementGroupFor(target.key());
        if (targetGroup.isEmpty()) {
            return PlacementDecision.groupPolicy(Placement.MODAL);
        }
        if (scene == null || scene.routedDescriptor() == null) {
            return PlacementDecision.groupPolicy(Placement.INLINE.primary());
        }
        Object routedKey = scene.routedDescriptor().blockKey();
        Optional<Group> routedGroup = blocks.placementGroupFor(routedKey);
        if (routedGroup.isEmpty()) {
            return PlacementDecision.groupPolicy(Placement.MODAL);
        }
        if (targetGroup.orElseThrow() == routedGroup.orElseThrow()) {
            return PlacementDecision.groupPolicy(Placement.MODAL);
        }
        return PlacementDecision.groupPolicy(Placement.INLINE.primary());
    }

    private static RuleMatch findBestRule(
            Class<? extends Block<?, ?>> blockClass,
            Map<? extends Class<? extends BlockRuntime>, Placement> placements) {
        RuleMatch best = null;
        for (Map.Entry<? extends Class<? extends BlockRuntime>, Placement> entry : placements.entrySet()) {
            Class<? extends BlockRuntime> ruleType = entry.getKey();
            if (!ruleType.isAssignableFrom(blockClass)) {
                continue;
            }
            int distance = inheritanceDistance(blockClass, ruleType);
            if (best == null || distance < best.distance()) {
                best = new RuleMatch(ruleType, entry.getValue(), distance);
            }
        }
        return best;
    }

    private static int inheritanceDistance(Class<?> source, Class<?> target) {
        if (source.equals(target)) {
            return 0;
        }
        Queue<ClassDepth> queue = new ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        queue.add(new ClassDepth(source, 0));
        while (!queue.isEmpty()) {
            ClassDepth current = queue.remove();
            Class<?> type = current.type();
            if (type == null || !seen.add(type)) {
                continue;
            }
            if (type.equals(target)) {
                return current.depth();
            }
            Class<?> superclass = type.getSuperclass();
            if (superclass != null) {
                queue.add(new ClassDepth(superclass, current.depth() + 1));
            }
            for (Class<?> iface : type.getInterfaces()) {
                queue.add(new ClassDepth(iface, current.depth() + 1));
            }
        }
        return Integer.MAX_VALUE;
    }

    private record RuleMatch(Class<? extends BlockRuntime> blockType,
                             Placement placement,
                             int distance) {}

    private record ClassDepth(Class<?> type, int depth) {}
}
