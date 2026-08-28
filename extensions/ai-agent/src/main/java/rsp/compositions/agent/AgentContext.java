package rsp.compositions.agent;

import rsp.compositions.block.BlockAction;
import rsp.compositions.block.BlockMetadata;

import rsp.component.Lookup;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.block.BlockRuntime;

import java.util.List;
import java.util.Objects;

/**
 * Runtime materialisation of agent scope.
 * <p>
 * Assembles description + actions per layer (framework, app, block),
 * applies {@link AgentActionFilter}, and provides a unified view for the agent.
 * <p>
 * Scope determines which layers are populated (additive):
 * <ul>
 *   <li>{@code BLOCK} — active block only</li>
 *   <li>{@code APP} — structure tree + active block</li>
 *   <li>{@code FRAMEWORK} — all levels + framework capabilities</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * AgentContext ctx = AgentContext.forScope(Scope.APP, activeBlock, structureTree, filter, lookup);
 * BlockProfile profile = ctx.blockProfile(); // pre-filtered
 * agentService.handlePrompt(text, profile, ctx.structureTree());
 * }</pre>
 */
public class AgentContext {

    public enum Scope { BLOCK, APP, FRAMEWORK }

    private final Scope scope;
    private final BlockRuntime activeBlock;
    private final StructureNode structureTree;
    private final AgentActionFilter filter;
    private final Lookup lookup;

    private AgentContext(Scope scope, BlockRuntime activeBlock,
                         StructureNode structureTree, AgentActionFilter filter,
                         Lookup lookup) {
        this.scope = Objects.requireNonNull(scope);
        this.activeBlock = activeBlock;
        this.structureTree = structureTree;
        this.filter = filter;
        this.lookup = Objects.requireNonNull(lookup);
    }

    /**
     * Create an AgentContext for the given scope.
     *
     * @param scope          the agent's scope level
     * @param activeBlock the currently active block (nullable)
     * @param structureTree  the app's navigation structure (nullable for BLOCK scope)
     * @param filter         action filter (nullable = no filtering)
     * @param lookup         the current context
     */
    public static AgentContext forScope(Scope scope, BlockRuntime activeBlock,
                                        StructureNode structureTree,
                                        AgentActionFilter filter, Lookup lookup) {
        return new AgentContext(scope, activeBlock, structureTree, filter, lookup);
    }

    // --- BlockRuntime layer ---

    /**
     * Structured metadata from the active block.
     *
     * @return metadata, or null if block doesn't expose metadata
     */
    public BlockMetadata blockMetadata() {
        if (activeBlock == null) return null;
        return activeBlock.blockMetadata();
    }

    /**
     * Actions available on the active block, with filter applied.
     */
    public List<BlockAction> blockActions() {
        if (activeBlock == null) return List.of();
        return applyFilter(activeBlock.agentActions());
    }

    // --- App layer ---

    /**
     * App-level structure description (group hierarchy with labels and descriptions).
     *
     * @return structure tree rendered as text, or null if not in scope
     */
    public String appDescription() {
        if (scope == Scope.BLOCK) return null;
        if (structureTree == null) return null;
        return structureTree.agentDescription();
    }

    // --- Framework layer ---

    /**
     * Framework-level description of block types and their general capabilities.
     *
     * @return framework description, or null if not in scope
     */
    public String frameworkDescription() {
        if (scope != Scope.FRAMEWORK) return null;
        return FRAMEWORK_DESCRIPTION;
    }

    // --- Composite accessors ---

    /**
     * Build a {@link BlockProfile} with filtered actions, for use by {@link AgentService}.
     */
    public BlockProfile blockProfile() {
        if (activeBlock == null) {
            return BlockProfile.of(null);
        }
        BlockMetadata metadata = blockMetadata();
        List<BlockAction> filteredActions = blockActions();
        return new BlockProfile(metadata, filteredActions, activeBlock.getClass());
    }

    public Scope scope() { return scope; }
    public BlockRuntime activeBlock() { return activeBlock; }
    public StructureNode structureTree() { return structureTree; }

    private List<BlockAction> applyFilter(List<BlockAction> actions) {
        if (filter == null) return actions;
        return filter.filter(actions, lookup);
    }

    private static final String FRAMEWORK_DESCRIPTION =
        "Block types:\n" +
        "  List view — paginated data list. Supports: create, edit, delete, page, select_all.\n" +
        "  Edit view — form for editing an existing entity. Supports: save, cancel, delete.\n" +
        "  Create view — form for creating a new entity. Supports: save, cancel.";
}
