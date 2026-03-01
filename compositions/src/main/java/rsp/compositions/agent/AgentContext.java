package rsp.compositions.agent;

import rsp.component.Lookup;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;

import java.util.List;
import java.util.Objects;

/**
 * Runtime materialisation of agent scope.
 * <p>
 * Assembles description + actions per layer (framework, app, contract),
 * applies {@link AgentActionFilter}, and provides a unified view for the agent.
 * <p>
 * Scope determines which layers are populated (additive):
 * <ul>
 *   <li>{@code CONTRACT} — active contract only</li>
 *   <li>{@code APP} — structure tree + active contract</li>
 *   <li>{@code FRAMEWORK} — all levels + framework capabilities</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * AgentContext ctx = AgentContext.forScope(Scope.APP, activeContract, structureTree, filter, lookup);
 * ContractProfile profile = ctx.contractProfile(); // pre-filtered
 * agentService.handlePrompt(text, profile, ctx.structureTree());
 * }</pre>
 */
public class AgentContext {

    public enum Scope { CONTRACT, APP, FRAMEWORK }

    private final Scope scope;
    private final ViewContract activeContract;
    private final StructureNode structureTree;
    private final AgentActionFilter filter;
    private final Lookup lookup;

    private AgentContext(Scope scope, ViewContract activeContract,
                         StructureNode structureTree, AgentActionFilter filter,
                         Lookup lookup) {
        this.scope = Objects.requireNonNull(scope);
        this.activeContract = activeContract;
        this.structureTree = structureTree;
        this.filter = filter;
        this.lookup = Objects.requireNonNull(lookup);
    }

    /**
     * Create an AgentContext for the given scope.
     *
     * @param scope          the agent's scope level
     * @param activeContract the currently active contract (nullable)
     * @param structureTree  the app's navigation structure (nullable for CONTRACT scope)
     * @param filter         action filter (nullable = no filtering)
     * @param lookup         the current context
     */
    public static AgentContext forScope(Scope scope, ViewContract activeContract,
                                        StructureNode structureTree,
                                        AgentActionFilter filter, Lookup lookup) {
        return new AgentContext(scope, activeContract, structureTree, filter, lookup);
    }

    // --- Contract layer ---

    /**
     * Live description of the active contract's state.
     *
     * @return natural-language description, or null if contract is not agent-discoverable
     */
    public String contractDescription() {
        if (activeContract instanceof AgentInfo info) {
            return info.agentDescription();
        }
        return activeContract != null ? activeContract.title() : null;
    }

    /**
     * Actions available on the active contract, with filter applied.
     */
    public List<AgentAction> contractActions() {
        if (activeContract == null) return List.of();
        return applyFilter(activeContract.agentActions());
    }

    // --- App layer ---

    /**
     * App-level structure description (group hierarchy with labels and descriptions).
     *
     * @return structure tree rendered as text, or null if not in scope
     */
    public String appDescription() {
        if (scope == Scope.CONTRACT) return null;
        if (structureTree == null) return null;
        return structureTree.agentDescription();
    }

    // --- Framework layer ---

    /**
     * Framework-level description of contract types and their general capabilities.
     *
     * @return framework description, or null if not in scope
     */
    public String frameworkDescription() {
        if (scope != Scope.FRAMEWORK) return null;
        return FRAMEWORK_DESCRIPTION;
    }

    // --- Composite accessors ---

    /**
     * Build a {@link ContractProfile} with filtered actions, for use by {@link AgentService}.
     */
    public ContractProfile contractProfile() {
        if (activeContract == null) {
            return ContractProfile.of(null);
        }
        String desc = contractDescription();
        List<AgentAction> filteredActions = contractActions();
        return new ContractProfile(desc, filteredActions, activeContract.getClass());
    }

    public Scope scope() { return scope; }
    public ViewContract activeContract() { return activeContract; }
    public StructureNode structureTree() { return structureTree; }

    private List<AgentAction> applyFilter(List<AgentAction> actions) {
        if (filter == null) return actions;
        return filter.filter(actions, lookup);
    }

    private static final String FRAMEWORK_DESCRIPTION =
        "Contract types:\n" +
        "  List view — paginated data list. Supports: create, edit, delete, page, select_all.\n" +
        "  Edit view — form for editing an existing entity. Supports: save, cancel, delete.\n" +
        "  Create view — form for creating a new entity. Supports: save, cancel.";
}
