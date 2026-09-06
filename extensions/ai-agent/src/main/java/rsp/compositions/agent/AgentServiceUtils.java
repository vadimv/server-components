package rsp.compositions.agent;

import rsp.compositions.block.Block;
import rsp.compositions.block.BlockTarget;

import rsp.compositions.block.BlockActionPayload;


import rsp.compositions.block.BlockAction;
import rsp.compositions.block.PayloadSchema;
import rsp.compositions.schema.FieldChoice;

import rsp.compositions.agent.AgentService.AgentResult;
import rsp.compositions.composition.StructureNode;
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
 * action lookup, block resolution, and state description.
 */
public final class AgentServiceUtils {

    private AgentServiceUtils() {}

    // ===== Tool Definitions =====

    /**
     * Builds the full set of tool definitions from a block profile and structure tree.
     * Includes action tools, navigate, plan, and text_reply.
     */
    public static List<ToolDefinition> buildToolDefinitions(BlockProfile profile,
                                                             StructureNode structureTree) {
        List<ToolDefinition> tools = new ArrayList<>();
        for (BlockAction action : profile.actions()) {
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
                                                       BlockProfile profile,
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

        // Infer navigate if no action but targetBlock is set
        String targetBlock = getString(output.value("targetBlock")).orElse("");
        if (action.isBlank() && !targetBlock.isBlank()) {
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
        BlockActionPayload payload = BlockActionPayload.ofNullable(rawJson);

        if ("navigate".equals(action)) {
            if (targetBlock.isBlank()
                    && payload.value() instanceof JsonDataType.String s && !s.value().isBlank()) {
                targetBlock = s.value();
            }
            BlockTarget target = resolveTarget(targetBlock, structureTree);
            if (target == null) {
                return Optional.of(new AgentResult.TextReply(
                    "I couldn't resolve navigation target: " + targetBlock));
            }
            return Optional.of(new AgentResult.NavigateResult(target));
        }

        // Look up the matching BlockAction from the block's declared actions
        BlockAction matchedAction = findAction(action, profile);
        if (matchedAction == null) {
            return Optional.of(new AgentResult.TextReply("Action not declared: " + action));
        }
        return Optional.of(new AgentResult.ActionResult(matchedAction, payload));
    }

    // ===== Action Lookup =====

    /**
     * Checks whether the action name is allowed in the given profile.
     */
    public static boolean isAllowedAction(String action, BlockProfile profile) {
        if ("navigate".equals(action)) {
            return true;
        }
        return findAction(action, profile) != null;
    }

    /**
     * Finds an action by name in the profile's declared actions.
     */
    public static BlockAction findAction(String actionName, BlockProfile profile) {
        for (BlockAction candidate : profile.actions()) {
            if (candidate.action().equals(actionName)) {
                return candidate;
            }
        }
        return null;
    }

    // ===== Block Resolution =====

    /**
     * Resolves a block class from a target name string by searching the structure tree.
     */
    public static Class<? extends Block<?, ?>> resolveTargetBlock(String targetName,
                                                                       StructureNode node) {
        BlockTarget target = resolveTarget(targetName, node);
        return target == null ? null : target.blockClass();
    }

    /** Resolve a configured target, preserving its binding key. */
    public static BlockTarget resolveTarget(String targetName, StructureNode node) {
        if (targetName == null || targetName.isBlank() || node == null) {
            return null;
        }
        return findTargetByName(targetName.trim(), node);
    }

    private static BlockTarget findTargetByName(String name, StructureNode node) {
        // Exact match on class name
        for (BlockTarget target : node.blockTargets()) {
            Class<? extends Block<?, ?>> block = target.blockClass();
            if (block.getSimpleName().equalsIgnoreCase(name)
                || block.getName().equalsIgnoreCase(name)) {
                return target;
            }
        }
        // Fuzzy match: class name contains the search term
        for (BlockTarget target : node.blockTargets()) {
            Class<? extends Block<?, ?>> block = target.blockClass();
            if (block.getSimpleName().toLowerCase(Locale.ROOT)
                    .contains(name.toLowerCase(Locale.ROOT))) {
                return target;
            }
        }
        // Match by node label
        if (node.label() != null && node.label().equalsIgnoreCase(name)
                && !node.blockTargets().isEmpty()) {
            return node.blockTargets().get(0);
        }
        for (StructureNode child : node.children()) {
            BlockTarget found = findTargetByName(name, child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ===== State Description =====

    /**
     * Describes the current block state for inclusion in system prompts.
     * Returns an empty string if no state is available.
     */
    @SuppressWarnings("unchecked")
    public static String describeState(BlockProfile profile) {
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
                    if (f.reference() != null) {
                        sb.append(", references ").append(f.reference().resourceKey());
                    }
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
            appendReferenceChoices(sb, state.get("references"));
        }

        return sb.toString();
    }

    private static void appendReferenceChoices(StringBuilder target, Object referenceState) {
        if (!(referenceState instanceof Map<?, ?> references) || references.isEmpty()) return;
        target.append("\nReference choices (submit the ID before '='):\n");
        references.forEach((fieldName, rawDescriptor) -> {
            target.append("- ").append(fieldName);
            if (!(rawDescriptor instanceof Map<?, ?> descriptor)) {
                target.append(": unavailable\n");
                return;
            }
            Object resource = descriptor.get("resource");
            if (resource != null) target.append(" [").append(resource).append("]");
            Object error = descriptor.get("error");
            if (error != null) {
                target.append(": unavailable (").append(error).append(")\n");
                return;
            }
            target.append(": ");
            if (descriptor.get("choices") instanceof List<?> choices && !choices.isEmpty()) {
                for (int i = 0; i < choices.size(); i++) {
                    if (i > 0) target.append("; ");
                    appendChoice(target, choices.get(i));
                }
            } else {
                target.append("none");
            }
            target.append("\n");
        });
    }

    private static void appendChoice(StringBuilder target, Object rawChoice) {
        if (rawChoice instanceof FieldChoice choice) {
            target.append(choice.value()).append(" = \"").append(choice.label()).append('"');
        } else if (rawChoice instanceof Map<?, ?> choice) {
            target.append(choice.get("value")).append(" = \"").append(choice.get("label")).append('"');
        } else {
            target.append(rawChoice);
        }
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
    public static String buildClassificationPrompt(BlockProfile profile, StructureNode structureTree) {
        String blockDesc = profile.metadata() != null
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
            %s""".formatted(blockDesc, structure, stateDesc);
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
    public static String buildExecutionPrompt(BlockProfile profile, StructureNode structureTree) {
        String blockDesc = profile.metadata() != null
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

            PLAN STEP CONSTRAINTS:
              * Each plan step MUST be a single, complete, concrete instruction. Avoid abstract
                directives like "make it more critical" or "the comment should express concerns" —
                instead emit "set field text to '<full concrete text>'" with the final value baked in.
              * For each form field, at most ONE "set field <name> to <value>" step per plan. Bake
                revisions/refinements into the single step's value before emitting it; do not write
                a draft and then overwrite it in a later step.
              * Resolve all references ("that post", "the latest item", "<id from visible items>")
                BEFORE writing the plan. Steps should not contain placeholders the runtime has to
                interpret.

            RULE 2 — SINGLE-ACTION SHORTCUTS (use only when RULE 1 does NOT apply):
              * "show posts", "go to comments" -> navigate tool with the exact block class name from App pages
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
            %s""".formatted(blockDesc, structure, stateDesc);
    }

    // ===== Tool Use → AgentResult =====

    /**
     * Converts a tool_use content block (tool name + parsed input) into an {@link AgentResult}.
     * Shared by services that use structured tool calling (Anthropic, OpenAI-compatible).
     */
    public static Optional<AgentResult> toolUseToAgentResult(String toolName,
                                                               JsonDataType.Object input,
                                                               BlockProfile profile,
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
            String targetBlock = getString(input.value("targetBlock")).orElse("");
            BlockTarget target = resolveTarget(targetBlock, structureTree);
            if (target == null) {
                return Optional.of(new AgentResult.TextReply(
                    "I couldn't resolve navigation target: " + targetBlock));
            }
            return Optional.of(new AgentResult.NavigateResult(target));
        }

        // Action tools — look up by name
        BlockAction matchedAction = findAction(toolName, profile);
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
        BlockActionPayload payload = BlockActionPayload.ofNullable(rawPayload);
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
