package rsp.app.posts.services;

import rsp.compositions.agent.AgentAction;
import rsp.compositions.agent.AgentPayload;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.ContractProfile;
import rsp.compositions.agent.PayloadSchemas;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Local LLM-backed agent service using the Ollama Chat API.
 *
 * <p>Responses are expected as strict JSON and validated against the current
 * contract profile before being translated into {@link AgentResult}.
 * If parsing/validation/network fails, it returns a text reply with the error.
 */
public final class OllamaAgentService extends AgentService {
    private static final System.Logger LOGGER =
            System.getLogger(OllamaAgentService.class.getName());

    private static final int DEBOUNCE_TOKENS = 5;
    private static final long DEBOUNCE_MS = 150;

    private final HttpClient httpClient;
    private final String endpoint;
    private final String model;
    private final Duration timeout;

    public OllamaAgentService(String endpoint, String model, Duration timeout) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.model = Objects.requireNonNull(model);
        this.timeout = Objects.requireNonNull(timeout);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
    }

    @Override
    public AgentResult handlePrompt(String prompt, ContractProfile profile, StructureNode structureTree) {
        return handlePrompt(prompt, profile, structureTree, _ -> {});
    }

    @Override
    public AgentResult handlePrompt(String prompt, ContractProfile profile,
                                    StructureNode structureTree,
                                    Consumer<String> onPartialContent) {
        try {
            Optional<AgentResult> parsed = parseWithOllamaStreaming(prompt, profile, structureTree, onPartialContent);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Ollama intent parse failed.", e);
            return new AgentResult.TextReply("LLM request failed: " + e.getClass().getSimpleName());
        }
        return new AgentResult.TextReply("LLM response could not be parsed as an intent.");
    }

    /**
     * Stream tokens from Ollama, pushing partial content to the callback as they arrive.
     * Debounces updates: pushes every {@code DEBOUNCE_MS} ms or every {@code DEBOUNCE_TOKENS} tokens.
     */
    private Optional<AgentResult> parseWithOllamaStreaming(String prompt, ContractProfile profile,
                                                           StructureNode structureTree,
                                                           Consumer<String> onPartialContent)
            throws IOException, InterruptedException {
        String requestBody = buildStreamingRequest(prompt, profile, structureTree);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.log(System.Logger.Level.WARNING,
                () -> "Ollama returned HTTP " + response.statusCode());
            return Optional.empty();
        }

        // Read NDJSON stream line by line, accumulate tokens
        StringBuilder accumulated = new StringBuilder();
        int tokensSinceLastPush = 0;
        long lastPushTime = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String token = extractToken(line);
                if (token != null && !token.isEmpty()) {
                    accumulated.append(token);
                    tokensSinceLastPush++;

                    long now = System.currentTimeMillis();
                    if (tokensSinceLastPush >= DEBOUNCE_TOKENS || (now - lastPushTime) >= DEBOUNCE_MS) {
                        onPartialContent.accept(accumulated.toString());
                        tokensSinceLastPush = 0;
                        lastPushTime = now;
                    }
                }

                // Check for stream end
                if (isStreamDone(line)) {
                    break;
                }
            }
        }

        // Final push with complete content
        String fullContent = accumulated.toString();
        if (!fullContent.isEmpty()) {
            onPartialContent.accept(fullContent);
        }

        LOGGER.log(System.Logger.Level.INFO,
            () -> "Ollama [" + prompt + "] -> " + fullContent);

        if (fullContent.isBlank()) {
            return Optional.empty();
        }

        JsonDataType modelOutput = JsonUtils.parse(fullContent);
        if (!(modelOutput instanceof JsonDataType.Object outputObj)) {
            return Optional.empty();
        }

        return toAgentResult(outputObj, profile, structureTree, prompt);
    }

    /**
     * Extract the token string from an Ollama NDJSON streaming line.
     * Each line is: {"model":"...","message":{"role":"assistant","content":"TOKEN"},"done":false}
     */
    private String extractToken(String jsonLine) {
        try {
            JsonDataType root = JsonUtils.parse(jsonLine);
            if (!(root instanceof JsonDataType.Object rootObj)) return null;
            JsonDataType messageNode = rootObj.value("message");
            if (!(messageNode instanceof JsonDataType.Object messageObj)) return null;
            JsonDataType contentNode = messageObj.value("content");
            if (!(contentNode instanceof JsonDataType.String contentStr)) return null;
            return contentStr.value();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if the NDJSON line indicates the stream is complete.
     */
    private boolean isStreamDone(String jsonLine) {
        try {
            JsonDataType root = JsonUtils.parse(jsonLine);
            if (!(root instanceof JsonDataType.Object rootObj)) return false;
            JsonDataType doneNode = rootObj.value("done");
            return doneNode instanceof JsonDataType.Boolean b && b.value();
        } catch (Exception e) {
            return false;
        }
    }

    private Optional<AgentResult> toAgentResult(JsonDataType.Object output, ContractProfile profile,
                                                StructureNode structureTree,
                                                String prompt) {
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

        // If the model set an action, treat as intent regardless of "type" field
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
        AgentPayload payload = AgentPayload.ofNullable(output.value("payload"));
        if ("navigate".equals(action)) {
            // Model sometimes puts contract name in payload instead of targetContract
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


    private boolean isAllowedAction(String action, ContractProfile profile) {
        if ("navigate".equals(action)) {
            return true;
        }
        return findAction(action, profile) != null;
    }

    private AgentAction findAction(String actionName, ContractProfile profile) {
        for (AgentAction candidate : profile.actions()) {
            if (candidate.action().equals(actionName)) {
                return candidate;
            }
        }
        return null;
    }

    private Class<? extends ViewContract> resolveTargetContract(String targetName, StructureNode node) {
        if (targetName == null || targetName.isBlank() || node == null) {
            return null;
        }
        String needle = targetName.trim();
        return findContractByName(needle, node);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends ViewContract> findContractByName(String name, StructureNode node) {
        // Exact match on class name
        for (Class<? extends ViewContract> contract : node.contracts()) {
            if (contract.getSimpleName().equalsIgnoreCase(name)
                || contract.getName().equalsIgnoreCase(name)) {
                return contract;
            }
        }
        // Fuzzy match: class name contains the search term (e.g. "Comments" matches "CommentsListContract")
        for (Class<? extends ViewContract> contract : node.contracts()) {
            if (contract.getSimpleName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
                return contract;
            }
        }
        // Match by node label (e.g. "Comments" matches the "Comments" group's first list contract)
        if (node.label() != null && node.label().equalsIgnoreCase(name) && !node.contracts().isEmpty()) {
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

    private Optional<String> getString(JsonDataType value) {
        return value instanceof JsonDataType.String s
            ? Optional.ofNullable(s.value())
            : Optional.empty();
    }

    private static final String JSON_SCHEMA = """
            {
              "type":"object",
              "properties":{
                "type":{"type":"string","enum":["intent","text","plan"]},
                "action":{"type":"string"},
                "payload":{"type":["string","integer","null"]},
                "targetContract":{"type":"string"},
                "message":{"type":"string"},
                "steps":{"type":"array","items":{"type":"string"}}
              },
              "required":["type"]
            }
            """;

    private String buildStreamingRequest(String userPrompt, ContractProfile profile, StructureNode structureTree) {
        String systemPrompt = buildSystemPrompt(profile, structureTree);
        return """
            {
              "model":"%s",
              "stream":true,
              "format":%s,
              "options":{"temperature":0},
              "messages":[
                {"role":"system","content":"%s"},
                {"role":"user","content":"%s"}
              ]
            }
            """.formatted(
                JsonUtils.escape(model),
                JSON_SCHEMA.strip(),
                JsonUtils.escape(systemPrompt),
                JsonUtils.escape(userPrompt));
    }

    private String buildSystemPrompt(ContractProfile profile, StructureNode structureTree) {
        StringBuilder actions = new StringBuilder();
        for (AgentAction action : profile.actions()) {
            actions.append("- action=\"")
                .append(action.action())
                .append("\": ")
                .append(action.description());
            String payloadDesc = PayloadSchemas.describe(action.schema());
            if (payloadDesc != null) {
                actions.append(" (payload: ").append(payloadDesc).append(")");
            }
            actions.append("\n");
        }

        String structure = structureTree != null
            ? structureTree.agentDescription()
            : "";

        String contractDesc = profile.metadata() != null
            ? profile.metadata().title() + " — " + profile.metadata().description()
            : "";

        String stateDesc = describeState(profile);

        return """
            You are an intent parser. Return ONE JSON object.
            IMPORTANT: When an action requires an item ID as payload, use the actual ID from the visible items below — NEVER use a name or description.

            Response types:
            - Single action: {"type": "intent", "action": "...", "payload": ..., "targetContract": "...", "message": "..."}
            - Multi-step plan: {"type": "plan", "steps": ["step 1 intent", "step 2 intent"], "message": "summary"}
            - Text reply: {"type": "text", "message": "..."}

            Allowed actions:
            %s- navigate: Go to a different page (set targetContract to the class name)

            Current page: %s
            App pages: %s
            %s
            Rules:
            - "show posts" or "go to comments" -> action=navigate, targetContract=exact class name from App pages (e.g. PostsListContract, CommentsListContract)
            - "page 3" or "goto page 2" -> action=page, payload=the number
            - "create" or "new" -> action=create
            - "edit 5" -> action=edit, payload=the item's ID (e.g. "5")
            - "delete 'Some Title'" -> action=delete, payload=the item's ID resolved from visible items
            - "select all" -> action=select_all
            - For greetings or general questions -> type=text, message=a friendly short reply
            """.formatted(
            actions.toString().strip(),
            contractDesc,
            structure,
            stateDesc);
    }

    @SuppressWarnings("unchecked")
    private String describeState(ContractProfile profile) {
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
}
