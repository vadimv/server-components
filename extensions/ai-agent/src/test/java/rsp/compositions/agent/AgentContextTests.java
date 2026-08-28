package rsp.compositions.agent;

import rsp.compositions.block.BlockAction;
import rsp.compositions.block.BlockMetadata;
import rsp.compositions.block.PayloadSchema;


import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.block.Block;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentContextTests {

    private static final BlockAction CREATE = new BlockAction("create",
        new EventKey.VoidKey("test.create"), "Create item");
    private static final BlockAction PAGE = new BlockAction("page",
        new EventKey.SimpleKey<>("test.page", Integer.class), "Navigate to page",
        new PayloadSchema.IntegerValue("page"));
    private static final BlockAction SELECT_ALL = new BlockAction("select_all",
        new EventKey.VoidKey("test.selectAll"), "Select all");

    private static final List<BlockAction> ACTIONS = List.of(CREATE, PAGE, SELECT_ALL);

    private static final StructureNode STRUCTURE = new StructureNode("Admin", "Administration panel",
        List.of(new StructureNode("Posts", "Blog posts", List.of(), List.of())),
        List.of());

    static class StubAgentBlock extends LookupBlock {
        StubAgentBlock(Lookup lookup) { super(lookup); }

        @Override
        public BlockMetadata blockMetadata() {
            return new BlockMetadata("Posts", "Paginated data list", null,
                Map.of("page", 1, "items", List.of()));
        }

        @Override
        public List<BlockAction> agentActions() { return ACTIONS; }

        @Override
        public String title() { return "Posts"; }
    }

    static class StubPlainBlock extends LookupBlock {
        StubPlainBlock(Lookup lookup) { super(lookup); }

        @Override
        public String title() { return "Plain"; }
    }

    abstract static class LookupBlock extends Block<Object, Object> {
        private final Lookup lookup;

        LookupBlock(Lookup lookup) { this.lookup = lookup; }

        @Override
        public Lookup lookup() { return lookup; }

        @Override
        public rsp.component.ComponentStateSupplier<Object> initStateSupplier() {
            return (_, _) -> new Object();
        }

        @Override
        public rsp.component.ComponentView<Object, Object> componentView() {
            return _ -> _ -> rsp.dsl.Html.text("");
        }
    }

    // --- BlockRuntime metadata ---

    @Test
    void block_scope_provides_block_metadata() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.BLOCK,
            block, STRUCTURE, null, lookup);

        BlockMetadata metadata = ctx.blockMetadata();
        assertNotNull(metadata);
        assertEquals("Posts", metadata.title());
    }

    @Test
    void block_scope_hides_app_description() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.BLOCK,
            block, STRUCTURE, null, lookup);

        assertNull(ctx.appDescription());
    }

    @Test
    void block_scope_hides_framework_description() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.BLOCK,
            block, STRUCTURE, null, lookup);

        assertNull(ctx.frameworkDescription());
    }

    @Test
    void block_scope_returns_all_actions_without_filter() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.BLOCK,
            block, STRUCTURE, null, lookup);

        assertEquals(3, ctx.blockActions().size());
    }

    // --- Scope: APP ---

    @Test
    void app_scope_provides_both_metadata_and_app_description() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            block, STRUCTURE, null, lookup);

        assertNotNull(ctx.blockMetadata());
        assertNotNull(ctx.appDescription());
        assertTrue(ctx.appDescription().contains("Admin"));
    }

    @Test
    void app_scope_hides_framework_description() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            block, STRUCTURE, null, lookup);

        assertNull(ctx.frameworkDescription());
    }

    // --- Scope: FRAMEWORK ---

    @Test
    void framework_scope_provides_all_layers() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.FRAMEWORK,
            block, STRUCTURE, null, lookup);

        assertNotNull(ctx.blockMetadata());
        assertNotNull(ctx.appDescription());
        assertNotNull(ctx.frameworkDescription());
        assertTrue(ctx.frameworkDescription().contains("List view"));
    }

    // --- Filtering ---

    @Test
    void filter_applied_to_block_actions() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);
        ReadOnlyFilter filter = new ReadOnlyFilter();

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            block, STRUCTURE, filter, lookup);

        List<BlockAction> actions = ctx.blockActions();
        assertEquals(2, actions.size());
        assertTrue(actions.stream().anyMatch(a -> "page".equals(a.action())));
        assertTrue(actions.stream().anyMatch(a -> "select_all".equals(a.action())));
        assertFalse(actions.stream().anyMatch(a -> "create".equals(a.action())));
    }

    @Test
    void blockProfile_uses_filtered_actions() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);
        ReadOnlyFilter filter = new ReadOnlyFilter();

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            block, STRUCTURE, filter, lookup);

        BlockProfile profile = ctx.blockProfile();
        assertNotNull(profile.metadata());
        assertEquals(2, profile.actions().size());
        assertEquals(StubAgentBlock.class, profile.blockClass());
    }

    @Test
    void blockProfile_without_filter_returns_all_actions() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            block, STRUCTURE, null, lookup);

        BlockProfile profile = ctx.blockProfile();
        assertEquals(3, profile.actions().size());
    }

    // --- Null block ---

    @Test
    void null_block_returns_empty_actions() {
        TestLookup lookup = new TestLookup();

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            null, STRUCTURE, null, lookup);

        assertTrue(ctx.blockActions().isEmpty());
        assertNull(ctx.blockMetadata());
    }

    @Test
    void null_block_profile_has_null_metadata() {
        TestLookup lookup = new TestLookup();

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            null, STRUCTURE, null, lookup);

        BlockProfile profile = ctx.blockProfile();
        assertNull(profile.metadata());
        assertTrue(profile.actions().isEmpty());
    }

    // --- BlockRuntime without metadata ---

    @Test
    void plain_block_returns_null_metadata() {
        TestLookup lookup = new TestLookup();
        StubPlainBlock block = new StubPlainBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            block, STRUCTURE, null, lookup);

        assertNull(ctx.blockMetadata());
    }

    // --- Structured metadata ---

    @Test
    void blockMetadata_returns_structured_data() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.BLOCK,
            block, STRUCTURE, null, lookup);

        BlockMetadata metadata = ctx.blockMetadata();
        assertNotNull(metadata);
        assertEquals("Posts", metadata.title());
        assertEquals("Paginated data list", metadata.description());
        assertEquals(1, metadata.state().get("page"));
    }

    @Test
    void blockProfile_includes_metadata() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            block, STRUCTURE, null, lookup);

        BlockProfile profile = ctx.blockProfile();
        assertNotNull(profile.metadata());
        assertEquals("Posts", profile.metadata().title());
    }

    // --- Accessors ---

    @Test
    void accessors_return_construction_values() {
        TestLookup lookup = new TestLookup();
        StubAgentBlock block = new StubAgentBlock(lookup);

        AgentContext ctx = AgentContext.forScope(AgentContext.Scope.APP,
            block, STRUCTURE, null, lookup);

        assertEquals(AgentContext.Scope.APP, ctx.scope());
        assertSame(block, ctx.activeBlock());
        assertSame(STRUCTURE, ctx.structureTree());
    }
}
