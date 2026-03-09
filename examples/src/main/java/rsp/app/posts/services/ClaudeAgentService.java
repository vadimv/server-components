package rsp.app.posts.services;

import rsp.compositions.agent.AgentAction;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.ContractProfile;
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
 * Claude API-backed agent service using the Anthropic Messages API.
 *
 * <p>Responses are expected as strict JSON and validated against the current
 * contract profile before being translated into {@link AgentIntent}.
 * If parsing/validation/network fails, it returns a text reply with the error.
 */
public final class ClaudeAgentService extends AgentService {
    private static final System.Logger LOGGER =
            System.getLogger(ClaudeAgentService.class.getName());

    private static final int DEBOUNCE_TOKENS = 5;
    private static final long DEBOUNCE_MS = 150;
    private static final String API_ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public ClaudeAgentService(String apiKey, String model, Duration timeout) {
        this.apiKey = Objects.requireNonNull(apiKey);
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
            Optional<AgentResult> parsed = parseWithClaudeStreaming(prompt, profile, structureTree, onPartialContent);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                "Claude intent parse failed.", e);
            return new AgentResult.TextReply("LLM request failed: " + e.getClass().getSimpleName());
        }
        return new AgentResult.TextReply("LLM response could not be parsed as an intent.");
    }

    @Override
    public void reset() {
        // stateless
    }

    /**
     * Stream tokens from Claude API, pushing partial content to the callback as they arrive.
     * Debounces updates: pushes every {@code DEBOUNCE_MS} ms or every {@code DEBOUNCE_TOKENS} tokens.
     */
    private Optional<AgentResult> parseWithClaudeStreaming(String prompt, ContractProfile profile,
                                                           StructureNode structureTree,
                                                           Consumer<String> onPartialContent)
            throws IOException, InterruptedException {
        String systemPrompt = buildSystemPrompt(profile, structureTree);
        String requestBody = buildStreamingRequest(prompt, systemPrompt);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_ENDPOINT))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.log(System.Logger.Level.WARNING,
                () -> "Claude API returned HTTP " + response.statusCode());
            return Optional.empty();
        }

        // Read SSE stream, accumulate content tokens
        StringBuilder accumulated = new StringBuilder();
        int tokensSinceLastPush = 0;
        long lastPushTime = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring("data: ".length());
                if ("[DONE]".equals(data)) break;

                String token = extractToken(data);
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

                // Check for message_stop event
                if (isStreamDone(data)) {
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
            () -> "Claude [" + prompt + "] -> " + fullContent);

        if (fullContent.isBlank()) {
            return Optional.empty();
        }

        // The model may wrap JSON in markdown code fences — strip them
        String jsonContent = stripCodeFences(fullContent);

        JsonDataType modelOutput = JsonUtils.parse(jsonContent);
        if (!(modelOutput instanceof JsonDataType.Object outputObj)) {
            return Optional.empty();
        }

        return toAgentResult(outputObj, profile, structureTree, prompt);
    }

    /**
     * Extract text delta from a Claude SSE event.
     * Event format: {"type":"content_block_delta","delta":{"type":"text_delta","text":"TOKEN"}}
     */
    private String extractToken(String jsonLine) {
        try {
            JsonDataType root = JsonUtils.parse(jsonLine);
            if (!(root instanceof JsonDataType.Object rootObj)) return null;
            String type = getString(rootObj.value("type")).orElse("");
            if (!"content_block_delta".equals(type)) return null;
            JsonDataType deltaNode = rootObj.value("delta");
            if (!(deltaNode instanceof JsonDataType.Object deltaObj)) return null;
            JsonDataType textNode = deltaObj.value("text");
            if (!(textNode instanceof JsonDataType.String textStr)) return null;
            return textStr.value();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if the SSE event indicates the stream is complete.
     */
    private boolean isStreamDone(String jsonLine) {
        try {
            JsonDataType root = JsonUtils.parse(jsonLine);
            if (!(root instanceof JsonDataType.Object rootObj)) return false;
            String type = getString(rootObj.value("type")).orElse("");
            return "message_stop".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Strip markdown code fences if the model wraps JSON in them.
     */
    private String stripCodeFences(String content) {
        String trimmed = content.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            return trimmed.strip();
        }
        return trimmed;
    }

    private Optional<AgentResult> toAgentResult(JsonDataType.Object output, ContractProfile profile,
                                                StructureNode structureTree,
                                                String prompt) {
        String type = getString(output.value("type")).orElse("").toLowerCase(Locale.ROOT);
        String action = getString(output.value("action")).orElse("");
        String message = getString(output.value("message")).orElse("");

        if ("text".equals(type) && action.isBlank()) {
            return Optional.of(new AgentResult.TextReply(
                message.isBlank() ? "I don't understand." : message));
        }

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
        Object payload = toJavaValue(output.value("payload"));
        // Unwrap single-element list — Claude sometimes returns ["12"] instead of "12"
        if (payload instanceof List<?> list && list.size() == 1) {
            payload = list.get(0);
        }
        Class<? extends ViewContract> target = null;
        if ("navigate".equals(action)) {
            if (targetContract.isBlank() && payload instanceof String s && !s.isBlank()) {
                targetContract = s;
                payload = null;
            }
            target = resolveTargetContract(targetContract, structureTree);
            if (target == null) {
                return Optional.of(new AgentResult.TextReply(
                    "I couldn't resolve navigation target: " + targetContract));
            }
        }

        Map<String, Object> params = payload != null ? Map.of("payload", payload) : Map.of();
        return Optional.of(new AgentResult.IntentResult(new AgentIntent(action, params, target)));
    }

    private boolean isAllowedAction(String action, ContractProfile profile) {
        if ("navigate".equals(action)) {
            return true;
        }
        for (AgentAction candidate : profile.actions()) {
            if (candidate.action().equals(action)) {
                return true;
            }
        }
        return false;
    }

    private Class<? extends ViewContract> resolveTargetContract(String targetName, StructureNode node) {
        if (targetName == null || targetName.isBlank() || node == null) {
            return null;
        }
        return findContractByName(targetName.trim(), node);
    }

    private Class<? extends ViewContract> findContractByName(String name, StructureNode node) {
        for (Class<? extends ViewContract> contract : node.contracts()) {
            if (contract.getSimpleName().equalsIgnoreCase(name)
                || contract.getName().equalsIgnoreCase(name)) {
                return contract;
            }
        }
        for (Class<? extends ViewContract> contract : node.contracts()) {
            if (contract.getSimpleName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
                return contract;
            }
        }
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

    private Object toJavaValue(JsonDataType value) {
        if (value == null || value instanceof JsonDataType.Null) {
            return null;
        }
        return switch (value) {
            case JsonDataType.String s -> s.value();
            case JsonDataType.Number n -> {
                if (n.isFractional()) {
                    yield n.value();
                }
                long l = n.asLong();
                yield (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) ? (int) l : l;
            }
            case JsonDataType.Boolean b -> b.value();
            case JsonDataType.Array array -> {
                List<Object> result = new ArrayList<>(array.size());
                for (int i = 0; i < array.size(); i++) {
                    result.add(toJavaValue(array.get(i)));
                }
                yield List.copyOf(result.stream().filter(Objects::nonNull).toList());
            }
            case JsonDataType.Object object -> {
                Map<String, Object> result = new LinkedHashMap<>();
                for (String key : object.keys()) {
                    result.put(key, toJavaValue(object.value(key)));
                }
                result.values().removeIf(Objects::isNull);
                yield Map.copyOf(result);
            }
            case JsonDataType.Null ignored -> null;
        };
    }

    private String buildStreamingRequest(String userPrompt, String systemPrompt) {
        return """
            {
              "model":"%s",
              "max_tokens":256,
              "stream":true,
              "system":"%s",
              "messages":[
                {"role":"user","content":"%s"}
              ]
            }
            """.formatted(
                JsonUtils.escape(model),
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
            if (action.payloadDescription() != null) {
                actions.append(" (payload: ").append(action.payloadDescription()).append(")");
            }
            actions.append("\n");
        }

        String structure = structureTree != null
            ? structureTree.agentDescription()
            : "";

        String contractDesc = Objects.toString(profile.description(), "");

        return """
            You are an intent parser. Return ONE JSON object with keys: type, action, payload, targetContract, message.
            Do NOT wrap the JSON in markdown code fences. Output raw JSON only.
            payload MUST be a single string, integer, or null — NEVER an array or object.

            Allowed actions:
            %s- navigate: Go to a different page (set targetContract to the class name)

            Current page: %s
            App pages: %s

            Rules:
            - "show posts" or "go to comments" -> action=navigate, targetContract=exact class name from App pages (e.g. PostsListContract, CommentsListContract)
            - "page 3" or "goto page 2" -> action=page, payload=the number
            - "create" or "new" -> action=create
            - "edit 5" -> action=edit, payload="5"
            - "delete" -> action=delete
            - "select all" -> action=select_all
            - For greetings or general questions -> type=text, message=a friendly short reply (e.g. "Hello! I can help you manage posts and comments. Try 'show posts' or 'create'.")
            """.formatted(
            actions.toString().strip(),
            contractDesc,
            structure);
    }
}
