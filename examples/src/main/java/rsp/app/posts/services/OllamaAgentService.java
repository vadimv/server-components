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
 * Local LLM-backed agent service using the Ollama Chat API with tool calling.
 *
 * <p>Sends structured {@link ToolDefinition}s in OpenAI-compatible format so that
 * tool-capable models return validated {@code tool_calls} instead of free-text JSON.
 * This eliminates the need for a constrained JSON {@code format} schema and the
 * fragility of parsing unstructured text.
 *
 * <p>Shared logic (tool building, action lookup, contract resolution) is
 * delegated to {@link AgentServiceUtils}.
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
                "Ollama intent parse failed.", e);
            return new AgentResult.TextReply("LLM request failed: " + e.getClass().getSimpleName());
        }
        return new AgentResult.TextReply("LLM response could not be parsed as an intent.");
    }

    /**
     * Stream tokens from Ollama with tool calling, pushing partial text content
     * to the callback as it arrives. Tool calls are extracted from the NDJSON
     * stream and converted to an {@link AgentResult} after completion.
     */
    private Optional<AgentResult> streamRequest(String prompt, List<ToolDefinition> tools,
                                                 String systemPrompt,
                                                 ContractProfile profile, StructureNode structureTree,
                                                 Consumer<String> onPartialContent)
            throws IOException, InterruptedException {
        String requestBody = buildStreamingRequest(prompt, systemPrompt, tools);

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

        // Track content from NDJSON stream
        StringBuilder textContent = new StringBuilder();
        String[] toolNameHolder = {null};
        JsonDataType.Object[] toolArgsHolder = {null};
        int tokensSinceLastPush = 0;
        long lastPushTime = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                try {
                    JsonDataType root = JsonUtils.parse(line);
                    if (!(root instanceof JsonDataType.Object rootObj)) continue;

                    JsonDataType messageNode = rootObj.value("message");
                    if (messageNode instanceof JsonDataType.Object messageObj) {
                        // Accumulate text content
                        String content = AgentServiceUtils.getString(
                            messageObj.value("content")).orElse("");
                        if (!content.isEmpty()) {
                            textContent.append(content);
                            tokensSinceLastPush++;
                            long now = System.currentTimeMillis();
                            if (tokensSinceLastPush >= DEBOUNCE_TOKENS
                                    || (now - lastPushTime) >= DEBOUNCE_MS) {
                                onPartialContent.accept(textContent.toString());
                                tokensSinceLastPush = 0;
                                lastPushTime = now;
                            }
                        }

                        // Check for tool calls (first one wins)
                        if (toolNameHolder[0] == null) {
                            JsonDataType toolCallsNode = messageObj.value("tool_calls");
                            if (toolCallsNode instanceof JsonDataType.Array toolCalls
                                    && toolCalls.size() > 0) {
                                JsonDataType firstCall = toolCalls.get(0);
                                if (firstCall instanceof JsonDataType.Object callObj) {
                                    JsonDataType functionNode = callObj.value("function");
                                    if (functionNode instanceof JsonDataType.Object funcObj) {
                                        toolNameHolder[0] = AgentServiceUtils.getString(
                                            funcObj.value("name")).orElse("");
                                        toolArgsHolder[0] = parseToolArguments(funcObj.value("arguments"));
                                    }
                                }
                            }
                        }
                    }

                    // Check for stream end
                    JsonDataType doneNode = rootObj.value("done");
                    if (doneNode instanceof JsonDataType.Boolean b && b.value()) {
                        break;
                    }
                } catch (Exception e) {
                    // Skip unparseable NDJSON lines
                }
            }
        }

        // Final push with complete text content
        if (!textContent.isEmpty()) {
            onPartialContent.accept(textContent.toString());
        }

        String toolName = toolNameHolder[0];
        JsonDataType.Object toolArguments = toolArgsHolder[0];
        LOGGER.log(System.Logger.Level.INFO,
            () -> "Ollama [" + prompt + "] -> tool=" + toolNameHolder[0] + " args=" + toolArgsHolder[0]);

        // If we got a tool call, convert to AgentResult
        if (toolName != null && !toolName.isEmpty() && toolArguments != null) {
            return AgentServiceUtils.toolUseToAgentResult(toolName, toolArguments, profile, structureTree);
        }

        // No tool call — treat accumulated text as a text reply
        String text = textContent.toString().strip();
        if (text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new AgentResult.TextReply(text));
    }

    /**
     * Extracts tool call arguments from the "arguments" node.
     * Ollama returns arguments as a JSON object; OpenAI-compatible APIs may
     * return a JSON string that needs parsing.
     */
    private JsonDataType.Object parseToolArguments(JsonDataType argsNode) {
        if (argsNode instanceof JsonDataType.Object obj) {
            return obj;
        }
        if (argsNode instanceof JsonDataType.String argsStr) {
            try {
                JsonDataType parsed = JsonUtils.parse(argsStr.value());
                if (parsed instanceof JsonDataType.Object obj) {
                    return obj;
                }
            } catch (Exception e) {
                // Fall through
            }
        }
        return null;
    }

    private String buildStreamingRequest(String userPrompt, String systemPrompt,
                                          List<ToolDefinition> tools) {
        StringBuilder toolsJson = new StringBuilder("[");
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) toolsJson.append(",");
            toolsJson.append(tools.get(i).toOpenAiJson());
        }
        toolsJson.append("]");

        return """
            {
              "model":"%s",
              "stream":true,
              "options":{"temperature":0},
              "messages":[
                {"role":"system","content":"%s"},
                {"role":"user","content":"%s"}
              ],
              "tools":%s,
              "tool_choice":"required"
            }
            """.formatted(
                JsonUtils.escape(model),
                JsonUtils.escape(systemPrompt),
                JsonUtils.escape(userPrompt),
                toolsJson.toString());
    }
}
