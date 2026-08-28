package rsp.compositions.agent;

import rsp.compositions.block.BlockAction;
import rsp.compositions.block.Block;
import rsp.compositions.block.PayloadSchema;


import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.compositions.composition.StructureNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionTests {

    @Test
    void fromAction_produces_valid_tool_definition() {
        BlockAction action = new BlockAction("page",
            new EventKey.SimpleKey<>("test.page", Integer.class),
            "Navigate to a page number",
            new PayloadSchema.IntegerValue("page number (1-based)"));

        ToolDefinition tool = ToolDefinition.fromAction(action);

        assertEquals("page", tool.name());
        assertEquals("Navigate to a page number", tool.description());
        assertTrue(tool.inputSchema().contains("\"type\":\"integer\""));
        assertTrue(tool.inputSchema().contains("page number (1-based)"));
    }

    @Test
    void fromAction_void_action_has_empty_properties() {
        BlockAction action = new BlockAction("select_all",
            new EventKey.VoidKey("test.selectAll"),
            "Select all rows");

        ToolDefinition tool = ToolDefinition.fromAction(action);

        assertEquals("select_all", tool.name());
        assertTrue(tool.inputSchema().contains("\"properties\":{}"));
    }

    @Test
    void navigateTool_includes_block_names_as_enum() {
        StructureNode tree = new StructureNode("Root", null,
            List.of(
                new StructureNode("Posts", null, List.of(), List.of(StubListBlock.class)),
                new StructureNode("Comments", null, List.of(), List.of(StubEditBlock.class))
            ),
            List.of());

        ToolDefinition tool = ToolDefinition.navigateTool(tree);

        assertEquals("navigate", tool.name());
        assertTrue(tool.inputSchema().contains("StubListBlock"));
        assertTrue(tool.inputSchema().contains("StubEditBlock"));
        assertTrue(tool.inputSchema().contains("\"enum\""));
    }

    @Test
    void planTool_has_steps_array() {
        ToolDefinition tool = ToolDefinition.planTool();

        assertEquals("plan", tool.name());
        assertTrue(tool.inputSchema().contains("\"steps\""));
        assertTrue(tool.inputSchema().contains("\"array\""));
        assertTrue(tool.inputSchema().contains("\"required\":[\"steps\"]"));
    }

    @Test
    void textReplyTool_has_message_field() {
        ToolDefinition tool = ToolDefinition.textReplyTool();

        assertEquals("text_reply", tool.name());
        assertTrue(tool.inputSchema().contains("\"message\""));
        assertTrue(tool.inputSchema().contains("\"required\":[\"message\"]"));
    }

    @Test
    void toAnthropicJson_uses_input_schema() {
        BlockAction action = new BlockAction("create",
            new EventKey.VoidKey("test.create"), "Create item");

        ToolDefinition tool = ToolDefinition.fromAction(action);
        String json = tool.toAnthropicJson();

        assertTrue(json.contains("\"name\":\"create\""));
        assertTrue(json.contains("\"description\":\"Create item\""));
        assertTrue(json.contains("\"input_schema\":"));
        assertFalse(json.contains("\"parameters\":"));
    }

    @Test
    void toOpenAiJson_uses_parameters() {
        BlockAction action = new BlockAction("create",
            new EventKey.VoidKey("test.create"), "Create item");

        ToolDefinition tool = ToolDefinition.fromAction(action);
        String json = tool.toOpenAiJson();

        assertTrue(json.contains("\"type\":\"function\""));
        assertTrue(json.contains("\"name\":\"create\""));
        assertTrue(json.contains("\"parameters\":"));
        assertFalse(json.contains("\"input_schema\":"));
    }

    // --- Stubs ---

    static abstract class StubListBlock extends Block<Object, Object> {
        @Override public rsp.component.Lookup lookup() { return null; }
        @Override public String title() { return "List"; }
    }

    static abstract class StubEditBlock extends Block<Object, Object> {
        @Override public rsp.component.Lookup lookup() { return null; }
        @Override public String title() { return "Edit"; }
    }
}
