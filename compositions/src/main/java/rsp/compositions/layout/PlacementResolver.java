package rsp.compositions.layout;

import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Resolves layout placement hints into effective decisions.
 */
public final class PlacementResolver {
    private PlacementResolver() {}

    public static PlacementDecision resolve(
            Class<? extends ViewContract> contractClass,
            Scene scene,
            Map<Class<? extends ViewContract>, Placement> placements,
            GroupPlacementPolicy groupPlacementPolicy) {
        Objects.requireNonNull(contractClass, "contractClass");
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(groupPlacementPolicy, "groupPlacementPolicy");

        RuleMatch match = findBestRule(contractClass, placements);
        if (match != null) {
            return PlacementDecision.layoutPlacement(match.placement(), match.contractType());
        }

        return switch (groupPlacementPolicy) {
            case ALL_INLINE -> PlacementDecision.groupPolicy(Placement.INLINE.primary());
            case FIRST_INLINE_OTHER_MODAL -> firstInlineOtherModal(scene);
            case ALL_MODAL -> PlacementDecision.groupPolicy(Placement.MODAL);
        };
    }

    private static PlacementDecision firstInlineOtherModal(Scene scene) {
        if (scene == null || scene.routedRuntime() == null) {
            return PlacementDecision.groupPolicy(Placement.INLINE.primary());
        }
        return PlacementDecision.groupPolicy(Placement.MODAL);
    }

    private static RuleMatch findBestRule(
            Class<? extends ViewContract> contractClass,
            Map<Class<? extends ViewContract>, Placement> placements) {
        RuleMatch best = null;
        for (var entry : placements.entrySet()) {
            Class<? extends ViewContract> ruleType = entry.getKey();
            if (!ruleType.isAssignableFrom(contractClass)) {
                continue;
            }
            int distance = inheritanceDistance(contractClass, ruleType);
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

    private record RuleMatch(Class<? extends ViewContract> contractType,
                             Placement placement,
                             int distance) {}

    private record ClassDepth(Class<?> type, int depth) {}
}
