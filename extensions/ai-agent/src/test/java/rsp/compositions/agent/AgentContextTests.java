package rsp.compositions.agent;

import rsp.compositions.contract.ContractAction;
import rsp.compositions.contract.ContractMetadata;
import rsp.compositions.contract.PayloadSchema;


import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentContextTests {

    private static final ContractAction CREATE = new ContractAction("create",
        new EventKey.VoidKey("test.create"), "Create item");
    private static final ContractAction PAGE = new ContractAction("page",
        new EventKey.SimpleKey<>("test.page", Integer.class), "Navigate to page",
        new PayloadSchema.IntegerValue("page"));
    private static final ContractAction SELECT_ALL = new ContractAction("select_all",
        new EventKey.VoidKey("test.selectAll"), "Select all");

    private static final List<ContractAction> ACTIONS = List.of(CREATE, PAGE, SELECT_ALL);

    private static final StructureNode STRUCTURE = new StructureNode("Admin", "Administration panel",
        List.of(new StructureNode("Posts", "Blog posts", List.of(), List.of())),
        List.of());

    static class StubAgentContract extends ViewContract {
        StubAgentContract(Lookup lookup) { super(lookup); }

        @Override
        public ContractMetadata contractMetadata() {
            return new ContractMetadata("Posts", "Paginated data list", null,
                Map.of("page", 1, "items", List.of()));
        }

        @Override
        public List<ContractAction> agentActions() { return ACTIONS; }

        @Override
        public ComponentContext enrichContext(ComponentContext ctx) { return ctx; }

        @Override
        public String title() { return "Posts"; }
    }

    static class StubPlainContract extends ViewContract {
        StubPlainContract(Lookup lookup) { super(lookup); }

        @Override
        public ComponentContext enrichContext(ComponentContext ctx) { return ctx; }

        @Override
        public String title() { return "Plain"; }
    }

    // --- Contract metadata ---

    @Test
    void contract_scope_provides_contract_metadata() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.CONTRACT,
            contract, STRUCTURE, null, lookup);

        ContractMetadata metadata = ctx.contractMetadata();
        assertNotNull(metadata);
        assertEquals("Posts", metadata.title());
    }

    @Test
    void contract_scope_hides_app_description() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.CONTRACT,
            contract, STRUCTURE, null, lookup);

        assertNull(ctx.appDescription());
    }

    @Test
    void contract_scope_hides_framework_description() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.CONTRACT,
            contract, STRUCTURE, null, lookup);

        assertNull(ctx.frameworkDescription());
    }

    @Test
    void contract_scope_returns_all_actions_without_filter() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.CONTRACT,
            contract, STRUCTURE, null, lookup);

        assertEquals(3, ctx.contractActions().size());
    }

    // --- Scope: APP ---

    @Test
    void app_scope_provides_both_metadata_and_app_description() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            contract, STRUCTURE, null, lookup);

        assertNotNull(ctx.contractMetadata());
        assertNotNull(ctx.appDescription());
        assertTrue(ctx.appDescription().contains("Admin"));
    }

    @Test
    void app_scope_hides_framework_description() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            contract, STRUCTURE, null, lookup);

        assertNull(ctx.frameworkDescription());
    }

    // --- Scope: FRAMEWORK ---

    @Test
    void framework_scope_provides_all_layers() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.FRAMEWORK,
            contract, STRUCTURE, null, lookup);

        assertNotNull(ctx.contractMetadata());
        assertNotNull(ctx.appDescription());
        assertNotNull(ctx.frameworkDescription());
        assertTrue(ctx.frameworkDescription().contains("List view"));
    }

    // --- Filtering ---

    @Test
    void filter_applied_to_contract_actions() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);
        ReadOnlyFilter filter = new ReadOnlyFilter();

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            contract, STRUCTURE, filter, lookup);

        List<ContractAction> actions = ctx.contractActions();
        assertEquals(2, actions.size());
        assertTrue(actions.stream().anyMatch(a -> "page".equals(a.action())));
        assertTrue(actions.stream().anyMatch(a -> "select_all".equals(a.action())));
        assertFalse(actions.stream().anyMatch(a -> "create".equals(a.action())));
    }

    @Test
    void contractProfile_uses_filtered_actions() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);
        ReadOnlyFilter filter = new ReadOnlyFilter();

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            contract, STRUCTURE, filter, lookup);

        ContractProfile profile = ctx.contractProfile();
        assertNotNull(profile.metadata());
        assertEquals(2, profile.actions().size());
        assertEquals(StubAgentContract.class, profile.contractClass());
    }

    @Test
    void contractProfile_without_filter_returns_all_actions() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            contract, STRUCTURE, null, lookup);

        ContractProfile profile = ctx.contractProfile();
        assertEquals(3, profile.actions().size());
    }

    // --- Null contract ---

    @Test
    void null_contract_returns_empty_actions() {
        TestLookup lookup = new TestLookup();

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            null, STRUCTURE, null, lookup);

        assertTrue(ctx.contractActions().isEmpty());
        assertNull(ctx.contractMetadata());
    }

    @Test
    void null_contract_profile_has_null_metadata() {
        TestLookup lookup = new TestLookup();

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            null, STRUCTURE, null, lookup);

        ContractProfile profile = ctx.contractProfile();
        assertNull(profile.metadata());
        assertTrue(profile.actions().isEmpty());
    }

    // --- Contract without metadata ---

    @Test
    void plain_contract_returns_null_metadata() {
        TestLookup lookup = new TestLookup();
        StubPlainContract contract = new StubPlainContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            contract, STRUCTURE, null, lookup);

        assertNull(ctx.contractMetadata());
    }

    // --- Structured metadata ---

    @Test
    void contractMetadata_returns_structured_data() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.CONTRACT,
            contract, STRUCTURE, null, lookup);

        ContractMetadata metadata = ctx.contractMetadata();
        assertNotNull(metadata);
        assertEquals("Posts", metadata.title());
        assertEquals("Paginated data list", metadata.description());
        assertEquals(1, metadata.state().get("page"));
    }

    @Test
    void contractProfile_includes_metadata() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            contract, STRUCTURE, null, lookup);

        ContractProfile profile = ctx.contractProfile();
        assertNotNull(profile.metadata());
        assertEquals("Posts", profile.metadata().title());
    }

    // --- Accessors ---

    @Test
    void accessors_return_construction_values() {
        TestLookup lookup = new TestLookup();
        StubAgentContract contract = new StubAgentContract(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            contract, STRUCTURE, null, lookup);

        assertEquals(AgentContext.Scope.APP, ctx.scope());
        assertSame(contract, ctx.activeContract());
        assertSame(STRUCTURE, ctx.structureTree());
    }
}
