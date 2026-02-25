package rsp.app.posts.services;

import rsp.compositions.agent.AgentIntent;
import rsp.compositions.contract.NavigationEntry;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless service that parses user prompts into {@link AgentIntent}s.
 * <p>
 * Prototype implementation using keyword matching (no LLM).
 * Returns null for unrecognized commands.
 */
public class AgentService {

    private static final Pattern PAGE_PATTERN = Pattern.compile("(?:go to )?page\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * Parse a user prompt into an agent intent.
     *
     * @param prompt     the user's text input
     * @param entries    navigation entries for contract discovery
     * @return an AgentIntent, or null if the prompt is not recognized
     */
    public AgentIntent handlePrompt(String prompt, List<NavigationEntry> entries) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }

        String text = prompt.trim().toLowerCase();

        // Try navigation: "show posts", "go to comments"
        AgentIntent nav = tryParseNavigation(text, entries);
        if (nav != null) return nav;

        // Try pagination: "page 3", "go to page 3"
        AgentIntent page = tryParsePagination(text);
        if (page != null) return page;

        // Selection commands
        if (text.equals("select all")) {
            return new AgentIntent("select_all");
        }

        // Edit selected
        if (text.equals("edit selected") || text.equals("edit selection")) {
            return new AgentIntent("edit");
        }

        // Create
        if (text.startsWith("create") || text.equals("new")) {
            return new AgentIntent("create");
        }

        return null;
    }

    private AgentIntent tryParseNavigation(String text, List<NavigationEntry> entries) {
        if (entries == null) return null;

        for (NavigationEntry entry : entries) {
            String label = entry.label().toLowerCase();
            if (text.equals("show " + label) || text.equals("go to " + label)
                    || text.equals(label)) {
                return new AgentIntent("navigate", Map.of(), entry.contractClass());
            }
        }
        return null;
    }

    private AgentIntent tryParsePagination(String text) {
        Matcher matcher = PAGE_PATTERN.matcher(text);
        if (matcher.matches()) {
            int page = Integer.parseInt(matcher.group(1));
            return new AgentIntent("page", Map.of("page", page));
        }
        return null;
    }
}
