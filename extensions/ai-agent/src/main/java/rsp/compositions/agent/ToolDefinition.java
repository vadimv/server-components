package rsp.compositions.agent;

import rsp.compositions.block.PayloadSchemas;


import rsp.compositions.block.BlockAction;

import rsp.compositions.composition.StructureNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Provider-neutral tool definition for LLM tool use APIs.
 * <p>
 * Carries the tool name, description, and JSON Schema for the input parameters.
 * Adapter code in each {@link AgentService} implementation converts these
 * to the provider-specific wire format ({@code input_schema} for Anthropic,
 * {@code parameters} for OpenAI-compatible APIs).
 *
 * @param name        tool name (e.g. "page", "delete", "navigate")
 * @param description human-readable purpose
 * @param inputSchema JSON Schema string for the tool's input parameters
 */
public record ToolDefinition(String name, String description, String inputSchema) {

    /**
     * Creates a tool definition from a block's declared action.
     */
    public static ToolDefinition fromAction(BlockAction action) {
        return new ToolDefinition(
            action.action(),
            action.description(),
            PayloadSchemas.toJsonSchema(action.schema())
        );
    }

    /**
     * Creates the "navigate" tool with an enum of available block targets.
     */
    public static ToolDefinition navigateTool(StructureNode tree) {
        Set<String> targetNames = new LinkedHashSet<>();
        collectTargetNames(tree, targetNames);
        List<String> blockNames = new ArrayList<>(targetNames);

        StringBuilder enumValues = new StringBuilder();
        for (int i = 0; i < blockNames.size(); i++) {
            if (i > 0) enumValues.append(",");
            enumValues.append("\"").append(escapeJson(blockNames.get(i))).append("\"");
        }

        String schema = "{\"type\":\"object\",\"properties\":{"
            + "\"targetBlock\":{\"type\":\"string\",\"enum\":[" + enumValues + "],"
            + "\"description\":\"Target block class or group label\"}"
            + "},\"required\":[\"targetBlock\"]}";

        return new ToolDefinition("navigate", "Navigate to a different page", schema);
    }

    /**
     * Creates the "plan" tool for multi-step execution.
     */
    public static ToolDefinition planTool() {
        String schema = "{\"type\":\"object\",\"properties\":{"
            + "\"steps\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},"
            + "\"description\":\"Natural language steps to execute sequentially\"},"
            + "\"message\":{\"type\":\"string\",\"description\":\"Brief summary of the plan\"}"
            + "},\"required\":[\"steps\"]}";

        return new ToolDefinition("plan", "Execute a multi-step plan", schema);
    }

    /**
     * Creates the "text_reply" tool for conversational responses.
     */
    public static ToolDefinition textReplyTool() {
        String schema = "{\"type\":\"object\",\"properties\":{"
            + "\"message\":{\"type\":\"string\",\"description\":\"The response message\"}"
            + "},\"required\":[\"message\"]}";

        return new ToolDefinition("text_reply", "Reply with a text message", schema);
    }

    /**
     * Formats this definition for the Anthropic tool use API.
     */
    public String toAnthropicJson() {
        return "{\"name\":\"" + escapeJson(name) + "\","
            + "\"description\":\"" + escapeJson(description) + "\","
            + "\"input_schema\":" + inputSchema + "}";
    }

    /**
     * Formats this definition for OpenAI-compatible APIs (OpenAI, Ollama).
     */
    public String toOpenAiJson() {
        return "{\"type\":\"function\",\"function\":{"
            + "\"name\":\"" + escapeJson(name) + "\","
            + "\"description\":\"" + escapeJson(description) + "\","
            + "\"parameters\":" + inputSchema + "}}";
    }

    private static void collectTargetNames(StructureNode node, Set<String> names) {
        if (node == null) return;
        if (node.label() != null && !node.blockTargets().isEmpty()) {
            names.add(node.label());
        }
        for (var target : node.blockTargets()) {
            names.add(target.blockClass().getSimpleName());
        }
        for (StructureNode child : node.children()) {
            collectTargetNames(child, names);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
