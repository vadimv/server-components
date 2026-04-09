package rsp.compositions.agent;

import rsp.compositions.contract.AgentPayload;


import rsp.compositions.contract.AgentAction;

import rsp.compositions.agent.AgentService.AgentResult;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;
import rsp.util.json.JsonDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared utilities for {@link AgentService} implementations.
 * <p>
 * Extracts common logic: tool definition building, JSON-to-AgentResult conversion,
 * action lookup, contract resolution, and state description.
 */
public final class AgentServiceUtils {

    private AgentServiceUtils() {}

    // ===== Tool Definitions =====

    /**
     * Builds the full set of tool definitions from a contract profile and structure tree.
     * Includes action tools, navigate, plan, and text_reply.
     */
    public static List<ToolDefinition> buildToolDefinitions(ContractProfile profile,
                                                             StructureNode structureTree) {
        List<ToolDefinition> tools = new ArrayList<>();
        for (AgentAction action : profile.actions()) {
            tools.add(ToolDefinition.fromAction(action));
        }
        if (structureTree != null) {
            tools.add(ToolDefinition.navigateTool(structureTree));
        }
        tools.add(ToolDefinition.planTool());
        tools.add(ToolDefinition.textReplyTool());
        return tools;
    }

    // ===== JSON Output → AgentResult =====

    /**
     * Converts a parsed JSON output object into an {@link AgentResult}.
     * Shared by services that use prompt-based (non-tool-use) JSON responses.
     */
    public static Optional<AgentResult> toAgentResult(JsonDataType.Object output,
                                                       ContractProfile profile,
                                                       StructureNode structureTree) {
        String type = getString(output.value("type")).orElse("").toLowerCase(Locale.ROOT);
        String action = getString(output.value("action")).orElse("");
        String message = getString(output.value("message")).orElse("");

        // Multi-step plan
        if ("plan".equals(type)) {
            JsonDataType stepsNode = output.value("steps");
            if (stepsNode instanceof JsonDataType.Array arr) {
                List<String> steps = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i) instanceof JsonDataType.String s) {
                        steps.add(s.value());
                    }
                }
                if (!steps.isEmpty()) {
                    return Optional.of(new AgentResult.PlanResult(steps, message));
                }
            }
            return Optional.empty();
        }

        // Text reply
        if ("text".equals(type) && action.isBlank()) {
            return Optional.of(new AgentResult.TextReply(
                message.isBlank() ? "I don't understand." : message));
        }

        // Infer navigate if no action but targetContract is set
        String targetContract = getString(output.value("targetContract")).orElse("");
        if (action.isBlank() && !targetContract.isBlank()) {
            action = "navigate";
        }

        if (action.isBlank()) {
            return Optional.empty();
        }

        if (!isAllowedAction(action, profile)) {
            return Optional.of(new AgentResult.TextReply("Action not allowed here: " + action));
        }

        JsonDataType rawJson = output.value("payload");
        // Unwrap single-element array — LLMs sometimes return ["12"] instead of "12"
        if (rawJson instanceof JsonDataType.Array arr && arr.size() == 1) {
            rawJson = arr.get(0);
        }
        AgentPayload payload = AgentPayload.ofNullable(rawJson);

        if ("navigate".equals(action)) {
            if (targetContract.isBlank()
                    && payload.value() instanceof JsonDataType.String s && !s.value().isBlank()) {
                targetContract = s.value();
            }
            Class<? extends ViewContract> target = resolveTargetContract(targetContract, structureTree);
            if (target == null) {
                return Optional.of(new AgentResult.TextReply(
                    "I couldn't resolve navigation target: " + targetContract));
            }
            return Optional.of(new AgentResult.NavigateResult(target));
        }

        // Look up the matching AgentAction from the contract's declared actions
        AgentAction matchedAction = findAction(action, profile);
        if (matchedAction == null) {
            return Optional.of(new AgentResult.TextReply("Action not declared: " + action));
        }
        return Optional.of(new AgentResult.ActionResult(matchedAction, payload));
    }

    // ===== Action Lookup =====

    /**
     * Checks whether the action name is allowed in the given profile.
     */
    public static boolean isAllowedAction(String action, ContractProfile profile) {
        if ("navigate".equals(action)) {
            return true;
        }
        return findAction(action, profile) != null;
    }

    /**
     * Finds an action by name in the profile's declared actions.
     */
    public static AgentAction findAction(String actionName, ContractProfile profile) {
        for (AgentAction candidate : profile.actions()) {
            if (candidate.action().equals(actionName)) {
                return candidate;
            }
        }
        return null;
    }

    // ===== Contract Resolution =====

    /**
     * Resolves a contract class from a target name string by searching the structure tree.
     */
    public static Class<? extends ViewContract> resolveTargetContract(String targetName,
                                                                       StructureNode node) {
        if (targetName == null || targetName.isBlank() || node == null) {
            return null;
        }
        return findContractByName(targetName.trim(), node);
    }

    private static Class<? extends ViewContract> findContractByName(String name, StructureNode node) {
        // Exact match on class name
        for (Class<? extends ViewContract> contract : node.contracts()) {
            if (contract.getSimpleName().equalsIgnoreCase(name)
                || contract.getName().equalsIgnoreCase(name)) {
                return contract;
            }
        }
        // Fuzzy match: class name contains the search term
        for (Class<? extends ViewContract> contract : node.contracts()) {
            if (contract.getSimpleName().toLowerCase(Locale.ROOT)
                    .contains(name.toLowerCase(Locale.ROOT))) {
                return contract;
            }
        }
        // Match by node label
        if (node.label() != null && node.label().equalsIgnoreCase(name)
                && !node.contracts().isEmpty()) {
            return node.contracts().get(0);
        }
        for (StructureNode child : node.children()) {
            Class<? extends ViewContract> found = findContractByName(name, child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ===== State Description =====

    /**
     * Describes the current contract state for inclusion in system prompts.
     * Returns an empty string if no state is available.
     */
    @SuppressWarnings("unchecked")
    public static String describeState(ContractProfile profile) {
        if (profile.metadata() == null) return "";
        Map<String, Object> state = profile.metadata().state();
        if (state.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\nVisible items:\n");
        if (state.get("items") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    sb.append("- ");
                    Map<String, Object> row = (Map<String, Object>) map;
                    Object id = row.get("id");
                    if (id != null) sb.append("id=").append(id);
                    for (Map.Entry<String, Object> e : row.entrySet()) {
                        if (!"id".equals(e.getKey())) {
                            sb.append(", ").append(e.getKey()).append("=").append(e.getValue());
                        }
                    }
                    sb.append("\n");
                }
            }
        } else if (state.get("entity") instanceof Map<?, ?> entity) {
            sb.append("Current entity: ");
            for (Map.Entry<?, ?> e : entity.entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append(", ");
            }
            sb.append("\n");
        } else {
            return "";
        }
        return sb.toString();
    }

    // ===== Classification Tools =====

    /**
     * Builds a minimal tool set for the classification phase: plan + text_reply only.
     * <p>
     * Forces the model to classify the request as either a plan (one or more actions)
     * or a text reply (greeting/question), avoiding the problem of the model picking
     * a specific action tool and ignoring the rest of a multi-step request.
     */
    public static List<ToolDefinition> buildClassificationTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ToolDefinition.planTool());
        tools.add(ToolDefinition.textReplyTool());
        return tools;
    }

    // ===== System Prompts =====

    /**
     * Builds a system prompt for the classification phase.
     * The model sees only plan and text_reply tools and must route accordingly.
     */
    public static String buildClassificationPrompt(ContractProfile profile, StructureNode structureTree) {
        String contractDesc = profile.metadata() != null
            ? profile.metadata().title() + " — " + profile.metadata().description()
            : "";

        String structure = structureTree != null
            ? structureTree.agentDescription()
            : "";

        String stateDesc = describeState(profile);

        return """
            You are an assistant for a web application.

            If the user wants to perform one or more actions, use the plan tool to list the steps as natural language phrases.
            Even for a single action (e.g. "show posts"), use the plan tool with one step.
            For greetings or general questions, use the text_reply tool with a friendly response.
            Plan steps MUST be natural language phrases (e.g. "go to page 2", "select all items").
            When a step involves a specific item, include enough context to identify it (e.g. "delete the post titled 'Hello World'").

            Current page: %s
            App pages: %s
            %s""".formatted(contractDesc, structure, stateDesc);
    }

    /**
     * Builds a system prompt for the execution phase (step-by-step).
     * The model sees the full tool set and executes a single action per call.
     */
    public static String buildExecutionPrompt(ContractProfile profile, StructureNode structureTree) {
        String contractDesc = profile.metadata() != null
            ? profile.metadata().title() + " — " + profile.metadata().description()
            : "";

        String structure = structureTree != null
            ? structureTree.agentDescription()
            : "";

        String stateDesc = describeState(profile);

        return """
            You are an assistant for a web application. Use the provided tools to fulfill user requests.

            Rules:
            - "show posts", "go to comments" -> navigate tool with the exact contract class name from App pages
            - "page 3", "goto page 2", "go to page N" -> page tool with the number as payload
            - "select all", "select all items", "select all rows", "select everything", "select all items on page N" -> select_all tool (no payload) — NEVER use the page tool for these
            - "delete selected", "delete all selected" -> delete_selected tool (no payload)
            - "create", "new" -> create tool
            - "edit 5" -> edit tool with the item's ID as payload
            - "delete 'Some Title'" -> resolve the item's ID from the visible items below, then use the delete tool with that ID
            - For greetings or general questions -> text_reply tool with a friendly short reply
            - IMPORTANT: When a tool requires an item ID as payload, use the actual ID from the visible items below — NEVER use a name or description

            Current page: %s
            App pages: %s
            %s""".formatted(contractDesc, structure, stateDesc);
    }

    // ===== Tool Use → AgentResult =====

    /**
     * Converts a tool_use content block (tool name + parsed input) into an {@link AgentResult}.
     * Shared by services that use structured tool calling (Anthropic, OpenAI-compatible).
     */
    public static Optional<AgentResult> toolUseToAgentResult(String toolName,
                                                               JsonDataType.Object input,
                                                               ContractProfile profile,
                                                               StructureNode structureTree) {
        if ("plan".equals(toolName)) {
            JsonDataType stepsNode = input.value("steps");
            if (stepsNode instanceof JsonDataType.Array arr) {
                List<String> steps = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i) instanceof JsonDataType.String s) {
                        steps.add(s.value());
                    }
                }
                String message = getString(input.value("message")).orElse("");
                if (!steps.isEmpty()) {
                    return Optional.of(new AgentResult.PlanResult(steps, message));
                }
            }
            return Optional.empty();
        }

        if ("text_reply".equals(toolName)) {
            String message = getString(input.value("message")).orElse("I don't understand.");
            return Optional.of(new AgentResult.TextReply(message));
        }

        if ("navigate".equals(toolName)) {
            String targetContract = getString(input.value("targetContract")).orElse("");
            Class<? extends ViewContract> target = resolveTargetContract(targetContract, structureTree);
            if (target == null) {
                return Optional.of(new AgentResult.TextReply(
                    "I couldn't resolve navigation target: " + targetContract));
            }
            return Optional.of(new AgentResult.NavigateResult(target));
        }

        // Action tools — look up by name
        AgentAction matchedAction = findAction(toolName, profile);
        if (matchedAction == null) {
            return Optional.of(new AgentResult.TextReply("Action not declared: " + toolName));
        }

        AgentPayload payload = AgentPayload.ofNullable(input.value("payload"));
        return Optional.of(new AgentResult.ActionResult(matchedAction, payload));
    }

    // ===== JSON Helpers =====

    /**
     * Extracts a string value from a JSON node.
     */
    public static Optional<String> getString(JsonDataType value) {
        return value instanceof JsonDataType.String s
            ? Optional.ofNullable(s.value())
            : Optional.empty();
    }
}
