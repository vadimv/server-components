package rsp.compositions.composition;

import rsp.compositions.contract.ViewContract;

import java.util.List;
import java.util.Objects;

/**
 * Lightweight metadata tree node extracted from {@link Group}.
 * <p>
 * Contains only labels and contract classes — no factories or views.
 * Used by navigation components (ExplorerContract), AI agents (PromptContract),
 * and other consumers that need the application's structural metadata.
 *
 * @param label       The display label for this node (nullable for unlabeled root groups)
 * @param description A natural-language description of this node's purpose (nullable)
 * @param children    Child structure nodes
 * @param contracts   Contract classes directly bound at this level
 */
public record StructureNode(String label,
                            String description,
                            List<StructureNode> children,
                            List<Class<? extends ViewContract>> contracts) {
    public StructureNode {
        Objects.requireNonNull(children, "children");
        Objects.requireNonNull(contracts, "contracts");
    }

    /**
     * Check if a contract class exists anywhere in this subtree.
     *
     * @param contractClass The contract class to search for
     * @return true if found at this level or in any descendant
     */
    public boolean contains(Class<? extends ViewContract> contractClass) {
        if (contracts.contains(contractClass)) {
            return true;
        }
        for (StructureNode child : children) {
            if (child.contains(contractClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the label of the node that contains the given contract class.
     * Searches this node first, then children depth-first.
     *
     * @param contractClass The contract class to search for
     * @return the label of the containing node, or null if not found
     */
    public String labelFor(Class<? extends ViewContract> contractClass) {
        if (contracts.contains(contractClass)) {
            return label;
        }
        for (StructureNode child : children) {
            String found = child.labelFor(contractClass);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Render this structure tree as a human-readable summary for AI agent consumption.
     * Includes labels, descriptions, and available sections.
     *
     * @return a multi-line text description of the application structure
     */
    public String agentDescription() {
        StringBuilder sb = new StringBuilder();
        renderAgentDescription(sb, 0);
        return sb.toString().stripTrailing();
    }

    private void renderAgentDescription(StringBuilder sb, int depth) {
        if (label != null) {
            sb.append("  ".repeat(depth));
            sb.append(label);
            if (description != null) {
                sb.append(" — ").append(description);
            }
            sb.append("\n");
        }
        for (StructureNode child : children) {
            child.renderAgentDescription(sb, label != null ? depth + 1 : depth);
        }
    }
}
