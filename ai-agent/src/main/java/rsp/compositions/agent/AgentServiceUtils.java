package rsp.compositions.agent;

import rsp.compositions.contract.ContractActionPayload;


import rsp.compositions.contract.ContractAction;
import rsp.compositions.contract.PayloadSchema;

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
        for (ContractAction action : profile.actions()) {
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
        ContractActionPayload payload = ContractActionPayload.ofNullable(rawJson);

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

        // Look up the matching ContractAction from the contract's declared actions
        ContractAction matchedAction = findAction(action, profile);
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
    public static ContractAction findAction(String actionName, ContractProfile profile) {
        for (ContractAction candidate : profile.actions()) {
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

        StringBuilder sb = new StringBuilder();

        // Form schema — surfaces the exact field names so set_field uses them
        // verbatim. Without this, the LLM has to guess from training data
        // (e.g. "content" for a comment whose actual field is "text"), and
        // any mismatch silently no-ops in the form's onMounted handler.
        if (profile.metadata().schema() != null) {
            var fields = profile.metadata().schema().fields().stream()
                    .filter(f -> !f.isHidden() && !f.isReadOnly())
                    .toList();
            if (!fields.isEmpty()) {
                sb.append("\nForm fields (use these exact names for the set_field tool):\n");
                for (var f : fields) {
                    sb.append("- ").append(f.name())
                      .append(" (").append(f.fieldType().name().toLowerCase(Locale.ROOT));
                    if (f.isRequired()) sb.append(", required");
                    sb.append(") — \"").append(f.displayName()).append("\"\n");
                }
            }
        }

        Map<String, Object> state = profile.metadata().state();
        if (!state.isEmpty()) {
            sb.append("\nVisible items:\n");
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
            }
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
     * Builds the system prompt for the unified agent loop.
     * <p>
     * The model sees the full tool set on every call. For multi-aspect
     * requests (navigate then act, multiple actions, etc.) it must use the
     * {@code plan} tool so the runtime can iterate through the steps;
     * otherwise it picks one action per call. Single-aspect plan steps
     * popped from the queue also land here, so the same prompt must work
     * for both initial requests and per-step execution.
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

            TOOL SELECTION (rules below are in priority order; first match wins):

            RULE 1 — PLAN FOR MULTI-STEP INTENT:
            Use the plan tool whenever the user's request implies more than one action. Plan steps
            are natural-language phrases that the runtime executes sequentially. Triggers:
              a) Phrases linked by "and", "then", "and then" — two or more actions.
              b) "create" / "edit" / "add" / "make" / "write" verbs with ANY of:
                 - a topic/subject ("about X", "for the topic of W"),
                 - an explicit value ("titled Y", "with content Z", "named N"),
                 - an attribute/adjective describing the new item ("positive", "negative",
                   "short", "long", "concise", "detailed"),
                 - a reference to an existing item ("to that post", "for this comment",
                   "the just-created X" — resolve the ID from "Visible items").
                 You MUST plan: a "create" / "edit" step followed by one "set field <name> to <value>"
                 step per provided/inferable value. Do NOT call create/edit/navigate alone when the
                 user gave any guidance — that guidance must flow into the form via set_field steps.
                 Field names MUST come from the "Form fields" listing in the prompt (which appears
                 once you are on the form).
              c) Navigate-then-act sequences (e.g. "go to posts page 2").
            Examples:
              * "create a new post about Japanese cuisine" ->
                  plan: ["create",
                         "set field title to 'A Brief Tour of Japanese Cuisine'",
                         "set field content to 'Japanese cuisine emphasises seasonality and simplicity...'"]
              * "add a positive comment to that post" ->
                  plan: ["show comments",
                         "create",
                         "set field text to 'Excellent overview — really enjoyed reading this.'",
                         "set field postId to '<post id from Visible items>'"]
                  (Resolve <post id> from the Visible items shown in the prompt's state section.)
              * "go to posts page 2" -> plan: ["show posts", "go to page 2"]
            Do NOT include a save/submit step in any plan — the user reviews and submits manually.

            RULE 2 — SINGLE-ACTION SHORTCUTS (use only when RULE 1 does NOT apply):
              * "show posts", "go to comments" -> navigate tool with the exact contract class name from App pages
              * "page 3", "goto page 2", "go to page N" -> page tool with the number as payload
              * "select all", "select all items", "select everything" -> select_all tool (no payload) — NEVER use page for these
              * "delete selected", "delete all selected" -> delete_selected tool (no payload)
              * "create" or "new" with NO content/title/details mentioned -> create tool (opens an empty form)
              * "edit 5" -> edit tool with the item's ID as payload
              * "delete 'Some Title'" -> resolve the item's ID from the visible items below, then use delete with that ID
              * "set field title to X", "fill content with Y" -> set_field tool with payload {"name": <field name>, "value": <value>}

            RULE 3 — GREETINGS / QUESTIONS: text_reply tool with a friendly short reply.

            IMPORTANT:
            - When a tool requires an item ID as payload, use the actual ID from the visible items below — NEVER use a name or description.
            - When filling a form, do NOT call save — stop after the last set_field. The user reviews and submits manually.

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
        ContractAction matchedAction = findAction(toolName, profile);
        if (matchedAction == null) {
            return Optional.of(new AgentResult.TextReply("Action not declared: " + toolName));
        }

        // Extract the payload from the tool input. Primitive schemas wrap the
        // value under "payload"; ObjectValue schemas put the structured fields
        // at the top level (the natural JSON Schema for objects). Mirror that
        // distinction here so the downstream parser sees what it expects.
        JsonDataType rawPayload = (matchedAction.schema() instanceof PayloadSchema.ObjectValue)
                ? input
                : input.value("payload");
        ContractActionPayload payload = ContractActionPayload.ofNullable(rawPayload);
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
