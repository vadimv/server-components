package rsp.compositions.composition;

import rsp.compositions.block.Block;
import rsp.compositions.block.BlockTarget;

import java.util.List;
import java.util.Objects;

/**
 * Lightweight metadata tree node extracted from {@link Group}.
 * <p>
 * Contains only labels and block targets — no factories or views.
 * Used by navigation components (ExplorerBlock), AI agents (PromptBlock),
 * and other consumers that need the application's structural metadata.
 *
 * @param label       The display label for this node (nullable for unlabeled root groups)
 * @param description A natural-language description of this node's purpose (nullable)
 * @param children    Child structure nodes
 * @param blocks      Block classes directly bound at this level
 * @param blockTargets Configured key/class pairs directly bound at this level
 */
public record StructureNode(String label,
                            String description,
                            List<StructureNode> children,
                            List<Class<? extends Block<?, ?>>> blocks,
                            List<BlockTarget> blockTargets) {
    public StructureNode(String label,
                         String description,
                         List<StructureNode> children,
                         List<Class<? extends Block<?, ?>>> blocks) {
        this(label, description, children, blocks,
                blocks.stream().map(blockClass -> new BlockTarget(blockClass, blockClass)).toList());
    }

    public StructureNode {
        Objects.requireNonNull(children, "children");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(blockTargets, "blockTargets");
    }

    /**
     * Check if a block class exists anywhere in this subtree.
     *
     * @param blockClass The block class to search for
     * @return true if found at this level or in any descendant
     */
    public boolean contains(Class<? extends Block<?, ?>> blockClass) {
        return contains((Object) blockClass);
    }

    /** Check if a binding key exists anywhere in this subtree. */
    public boolean contains(Object blockKey) {
        if (blockTargets.stream().anyMatch(target -> target.key().equals(blockKey))) {
            return true;
        }
        for (StructureNode child : children) {
            if (child.contains(blockKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the label of the node that contains the given block class.
     * Searches this node first, then children depth-first.
     *
     * @param blockClass The block class to search for
     * @return the label of the containing node, or null if not found
     */
    public String labelFor(Class<? extends Block<?, ?>> blockClass) {
        return labelFor((Object) blockClass);
    }

    /** Find the label of the node containing the binding key. */
    public String labelFor(Object blockKey) {
        if (blockTargets.stream().anyMatch(target -> target.key().equals(blockKey))) {
            return label;
        }
        for (StructureNode child : children) {
            String found = child.labelFor(blockKey);
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
            if (!blocks.isEmpty()) {
                sb.append(" [");
                for (int i = 0; i < blocks.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(blocks.get(i).getSimpleName());
                }
                sb.append("]");
            }
            sb.append("\n");
        }
        for (StructureNode child : children) {
            child.renderAgentDescription(sb, label != null ? depth + 1 : depth);
        }
    }
}
