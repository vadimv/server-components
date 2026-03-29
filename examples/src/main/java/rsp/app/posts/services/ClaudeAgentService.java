package rsp.app.posts.services;

import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AgentServiceUtils;
import rsp.compositions.agent.ContractProfile;
import rsp.compositions.agent.ToolDefinition;
import rsp.compositions.composition.StructureNode;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Claude API-backed agent service using the Anthropic Messages API with tool use.
 *
 * <p>Sends structured {@link ToolDefinition}s so that Claude returns validated
 * {@code tool_use} content blocks instead of free-text JSON. This eliminates
 * parsing fragility (prose-prefixed JSON, markdown fences, schema mismatches).
 *
 * <p>Shared logic (tool building, action lookup, contract resolution) is
 * delegated to {@link AgentServiceUtils}.
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

    /**
     * Execution phase: full tool set for single-action step execution.
     * Called by plan executor for each individual step.
     */
    @Override
    public AgentResult handlePrompt(String prompt, ContractProfile profile, StructureNode structureTree) {
        return doHandlePrompt(prompt,
            AgentServiceUtils.buildToolDefinitions(profile, structureTree),
            AgentServiceUtils.buildExecutionPrompt(profile, structureTree),
            profile, structureTree, _ -> {});
    }

    /**
     * Classification phase: only plan + text_reply tools.
     * Called for the initial user prompt to classify as plan or greeting.
     */
    @Override
    public AgentResult handlePrompt(String prompt, ContractProfile profile,
                                    StructureNode structureTree,
                                    Consumer<String> onPartialContent) {
        return doHandlePrompt(prompt,
            AgentServiceUtils.buildClassificationTools(),
            AgentServiceUtils.buildClassificationPrompt(profile, structureTree),
            profile, structureTree, onPartialContent);
    }

    private AgentResult doHandlePrompt(String prompt, List<ToolDefinition> tools,
                                        String systemPrompt,
                                        ContractProfile profile, StructureNode structureTree,
                                        Consumer<String> onPartialContent) {
        try {
            Optional<AgentResult> parsed = streamRequest(prompt, tools, systemPrompt,
                profile, structureTree, onPartialContent);
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

    /**
     * Stream tokens from Claude API with tool use, pushing partial text content
     * to the callback as it arrives. Tool use blocks are accumulated silently
     * and converted to an {@link AgentResult} after the stream completes.
     */
    private Optional<AgentResult> streamRequest(String prompt, List<ToolDefinition> tools,
                                                 String systemPrompt,
                                                 ContractProfile profile, StructureNode structureTree,
                                                 Consumer<String> onPartialContent)
            throws IOException, InterruptedException {
        String requestBody = buildStreamingRequest(prompt, systemPrompt, tools);

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

        // Track content blocks from SSE stream
        StringBuilder textContent = new StringBuilder();
        String[] toolNameHolder = {null};
        StringBuilder toolInputJson = new StringBuilder();
        boolean capturingToolInput = false;
        int tokensSinceLastPush = 0;
        long lastPushTime = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring("data: ".length());
                if ("[DONE]".equals(data)) break;

                try {
                    JsonDataType root = JsonUtils.parse(data);
                    if (!(root instanceof JsonDataType.Object rootObj)) continue;
                    String eventType = AgentServiceUtils.getString(rootObj.value("type")).orElse("");

                    switch (eventType) {
                        case "content_block_start" -> {
                            JsonDataType blockNode = rootObj.value("content_block");
                            if (blockNode instanceof JsonDataType.Object block) {
                                String blockType = AgentServiceUtils.getString(
                                    block.value("type")).orElse("");
                                if ("tool_use".equals(blockType) && toolNameHolder[0] == null) {
                                    toolNameHolder[0] = AgentServiceUtils.getString(
                                        block.value("name")).orElse("");
                                    capturingToolInput = true;
                                }
                            }
                        }
                        case "content_block_delta" -> {
                            JsonDataType deltaNode = rootObj.value("delta");
                            if (deltaNode instanceof JsonDataType.Object delta) {
                                String deltaType = AgentServiceUtils.getString(
                                    delta.value("type")).orElse("");
                                if ("text_delta".equals(deltaType)) {
                                    String text = AgentServiceUtils.getString(
                                        delta.value("text")).orElse("");
                                    textContent.append(text);
                                    tokensSinceLastPush++;
                                    long now = System.currentTimeMillis();
                                    if (tokensSinceLastPush >= DEBOUNCE_TOKENS
                                            || (now - lastPushTime) >= DEBOUNCE_MS) {
                                        onPartialContent.accept(textContent.toString());
                                        tokensSinceLastPush = 0;
                                        lastPushTime = now;
                                    }
                                } else if ("input_json_delta".equals(deltaType) && capturingToolInput) {
                                    String partial = AgentServiceUtils.getString(
                                        delta.value("partial_json")).orElse("");
                                    toolInputJson.append(partial);
                                }
                            }
                        }
                        case "content_block_stop" -> {
                            if (capturingToolInput) {
                                capturingToolInput = false;
                            }
                        }
                        case "message_stop" -> { /* stream is done */ }
                        default -> { /* ignore other event types */ }
                    }
                } catch (Exception e) {
                    // Skip unparseable SSE events
                }
            }
        }

        // Final push with complete text content
        if (!textContent.isEmpty()) {
            onPartialContent.accept(textContent.toString());
        }

        String toolName = toolNameHolder[0];
        LOGGER.log(System.Logger.Level.INFO,
            () -> "Claude [" + prompt + "] -> tool=" + toolNameHolder[0] + " input=" + toolInputJson);

        // If we got a tool use, convert to AgentResult
        if (toolName != null && !toolName.isEmpty()) {
            return toolUseToAgentResult(toolName, toolInputJson.toString(), profile, structureTree);
        }

        // No tool use — treat accumulated text as a text reply
        String text = textContent.toString().strip();
        if (text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new AgentResult.TextReply(text));
    }

    /**
     * Parses accumulated tool input JSON and delegates to the shared converter.
     */
    private Optional<AgentResult> toolUseToAgentResult(String toolName, String inputJson,
                                                        ContractProfile profile,
                                                        StructureNode structureTree) {
        try {
            JsonDataType parsed = JsonUtils.parse(inputJson.isBlank() ? "{}" : inputJson);
            if (parsed instanceof JsonDataType.Object input) {
                return AgentServiceUtils.toolUseToAgentResult(toolName, input, profile, structureTree);
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to parse tool input JSON", e);
        }
        return Optional.empty();
    }

    private String buildStreamingRequest(String userPrompt, String systemPrompt,
                                          List<ToolDefinition> tools) {
        StringBuilder toolsJson = new StringBuilder("[");
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) toolsJson.append(",");
            toolsJson.append(tools.get(i).toAnthropicJson());
        }
        toolsJson.append("]");

        return """
            {
              "model":"%s",
              "max_tokens":1024,
              "stream":true,
              "system":"%s",
              "tools":%s,
              "tool_choice":{"type":"any"},
              "messages":[
                {"role":"user","content":"%s"}
              ]
            }
            """.formatted(
                JsonUtils.escape(model),
                JsonUtils.escape(systemPrompt),
                toolsJson.toString(),
                JsonUtils.escape(userPrompt));
    }
}
