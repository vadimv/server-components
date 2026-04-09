package rsp.compositions.agent;

import rsp.compositions.contract.PayloadSchemas;


import rsp.compositions.contract.AgentAction;

import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;

import java.util.ArrayList;
import java.util.List;

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
     * Creates a tool definition from a contract's declared action.
     */
    public static ToolDefinition fromAction(AgentAction action) {
        return new ToolDefinition(
            action.action(),
            action.description(),
            PayloadSchemas.toJsonSchema(action.schema())
        );
    }

    /**
     * Creates the "navigate" tool with an enum of available contract targets.
     */
    public static ToolDefinition navigateTool(StructureNode tree) {
        List<String> contractNames = new ArrayList<>();
        collectContractNames(tree, contractNames);

        StringBuilder enumValues = new StringBuilder();
        for (int i = 0; i < contractNames.size(); i++) {
            if (i > 0) enumValues.append(",");
            enumValues.append("\"").append(contractNames.get(i)).append("\"");
        }

        String schema = "{\"type\":\"object\",\"properties\":{"
            + "\"targetContract\":{\"type\":\"string\",\"enum\":[" + enumValues + "],"
            + "\"description\":\"Target contract class name\"}"
            + "},\"required\":[\"targetContract\"]}";

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

    private static void collectContractNames(StructureNode node, List<String> names) {
        if (node == null) return;
        for (Class<? extends ViewContract> contract : node.contracts()) {
            names.add(contract.getSimpleName());
        }
        for (StructureNode child : node.children()) {
            collectContractNames(child, names);
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
