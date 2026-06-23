package rsp.compositions.contract;

import java.util.List;
import java.util.Objects;

/**
 * Hierarchical navigation node — a tree of groups and routable entries used by
 * navigation/explorer UIs to render menus.
 * <p>
 * A node may be:
 * <ul>
 *   <li>A pure group (label != null, entry == null) — renders as a header with children below.</li>
 *   <li>A routable leaf (entry != null) — renders as a clickable item.</li>
 *   <li>An anonymous root (label == null, entry == null) — a holder whose children render inline.</li>
 * </ul>
 * A node may carry both an entry and children (a clickable group with nested items).
 *
 * @param label    display label, or null for an anonymous root
 * @param entry    navigation target for this node, or null for a non-routable group
 * @param children child nodes, in declaration order
 */
public record NavigationNode(String label,
                             NavigationEntry entry,
                             List<NavigationNode> children) {
    public NavigationNode {
        Objects.requireNonNull(children, "children");
    }
}
