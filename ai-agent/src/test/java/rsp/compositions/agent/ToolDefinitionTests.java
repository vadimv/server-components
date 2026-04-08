package rsp.compositions.agent;

import rsp.compositions.contract.AgentAction;
import rsp.compositions.contract.PayloadSchema;


import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionTests {

    @Test
    void fromAction_produces_valid_tool_definition() {
        AgentAction action = new AgentAction("page",
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
        AgentAction action = new AgentAction("select_all",
            new EventKey.VoidKey("test.selectAll"),
            "Select all rows");

        ToolDefinition tool = ToolDefinition.fromAction(action);

        assertEquals("select_all", tool.name());
        assertTrue(tool.inputSchema().contains("\"properties\":{}"));
    }

    @Test
    void navigateTool_includes_contract_names_as_enum() {
        StructureNode tree = new StructureNode("Root", null,
            List.of(
                new StructureNode("Posts", null, List.of(), List.of(StubListContract.class)),
                new StructureNode("Comments", null, List.of(), List.of(StubEditContract.class))
            ),
            List.of());

        ToolDefinition tool = ToolDefinition.navigateTool(tree);

        assertEquals("navigate", tool.name());
        assertTrue(tool.inputSchema().contains("StubListContract"));
        assertTrue(tool.inputSchema().contains("StubEditContract"));
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
        AgentAction action = new AgentAction("create",
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
        AgentAction action = new AgentAction("create",
            new EventKey.VoidKey("test.create"), "Create item");

        ToolDefinition tool = ToolDefinition.fromAction(action);
        String json = tool.toOpenAiJson();

        assertTrue(json.contains("\"type\":\"function\""));
        assertTrue(json.contains("\"name\":\"create\""));
        assertTrue(json.contains("\"parameters\":"));
        assertFalse(json.contains("\"input_schema\":"));
    }

    // --- Stubs ---

    static abstract class StubListContract extends ViewContract {
        StubListContract() { super(null); }
    }

    static abstract class StubEditContract extends ViewContract {
        StubEditContract() { super(null); }
    }
}
