package rsp.compositions.agent;

import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stub agent service — parses natural-language prompts into {@link AgentIntent}s
 * using simple regex matching. No LLM dependency.
 * <p>
 * Supported prompts:
 * <ul>
 *   <li>{@code delete 'Post Title 1'} or {@code delete Post Title 1} — resolve item by name, emit delete intent</li>
 *   <li>{@code search all posts with id < 2} — filter visible items, return text reply</li>
 *   <li>{@code update post 2 adding 'test'} — two-step: open edit, then save with modification</li>
 *   <li>{@code show posts} — navigate to matching group</li>
 *   <li>{@code go to page 3} — pagination</li>
 *   <li>{@code select all} — select all rows on the current page</li>
 *   <li>{@code edit selected} — open editor for the first selected item</li>
 * </ul>
 */
public class AgentService {

    /**
     * Result of processing a prompt.
     */
    public sealed interface AgentResult {
        /** An intent to be dispatched via IntentGate → IntentDispatcher. */
        record IntentResult(AgentIntent intent) implements AgentResult {}
        /** A text reply to show the user (no framework event). */
        record TextReply(String message) implements AgentResult {}
    }

    /**
     * Internal state for multi-step interactions.
     */
    sealed interface AgentState {
        record Idle() implements AgentState {}
        record PendingSave(String modification) implements AgentState {}
    }

    // Patterns
    private static final Pattern DELETE_QUOTED_PATTERN = Pattern.compile(
        "delete\\s+['\"](.+?)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_UNQUOTED_PATTERN = Pattern.compile(
        "delete\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_PATTERN = Pattern.compile(
        "search\\s+.*?with\\s+(\\w+)\\s*([<>=!]+)\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
        "update\\s+.*?(\\d+)\\s+adding\\s+['\"](.+?)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_PATTERN = Pattern.compile(
        "(?:go to\\s+)?page\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_ALL_PATTERN = Pattern.compile(
        "select\\s+all", Pattern.CASE_INSENSITIVE);
    private static final Pattern EDIT_SELECTED_PATTERN = Pattern.compile(
        "edit\\s+selected", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAVIGATE_PATTERN = Pattern.compile(
        "(?:show|open|go to)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    // Pattern to extract items from agentDescription()
    private static final Pattern ITEM_LINE_PATTERN = Pattern.compile(
        "^\\s*\\S+\\[(.+)]\\s*$");

    private AgentState state = new AgentState.Idle();

    /**
     * Process a user prompt against the active contract's profile and structure tree.
     *
     * @param prompt        the user's natural-language input
     * @param profile       the active contract's profile (description + actions)
     * @param structureTree the navigation structure
     * @return the result (intent or text reply)
     */
    public AgentResult handlePrompt(String prompt, ContractProfile profile,
                                    StructureNode structureTree) {
        // Step 2 of update flow: we're waiting for the edit contract to be active
        if (state instanceof AgentState.PendingSave pending) {
            state = new AgentState.Idle();
            return handlePendingSave(pending.modification(), profile);
        }

        Matcher m;

        // Delete by name: delete 'Post Title 1' or delete Post Title 1
        m = DELETE_QUOTED_PATTERN.matcher(prompt);
        if (m.find()) {
            return handleDelete(m.group(1), profile);
        }

        // Select all rows
        m = SELECT_ALL_PATTERN.matcher(prompt);
        if (m.find()) {
            return new AgentResult.IntentResult(new AgentIntent("select_all"));
        }

        // Edit selected item
        m = EDIT_SELECTED_PATTERN.matcher(prompt);
        if (m.find()) {
            return handleEditSelected(profile);
        }

        // Search/filter: search all posts with id < 2
        m = SEARCH_PATTERN.matcher(prompt);
        if (m.find()) {
            return handleSearch(m.group(1), m.group(2), m.group(3), profile);
        }

        // Update: update post 2 adding 'test'
        m = UPDATE_PATTERN.matcher(prompt);
        if (m.find()) {
            return handleUpdate(m.group(1), m.group(2), profile);
        }

        // Pagination: go to page 3
        m = PAGE_PATTERN.matcher(prompt);
        if (m.find()) {
            int page = Integer.parseInt(m.group(1));
            return new AgentResult.IntentResult(
                new AgentIntent("page", Map.of("payload", page)));
        }

        // Navigation: show posts
        m = NAVIGATE_PATTERN.matcher(prompt);
        if (m.find()) {
            return handleNavigate(m.group(1), structureTree);
        }

        // Delete without quotes (last — broad match)
        m = DELETE_UNQUOTED_PATTERN.matcher(prompt);
        if (m.find()) {
            return handleDelete(m.group(1).trim(), profile);
        }

        return new AgentResult.TextReply("I don't understand: " + prompt);
    }

    /**
     * Reset the agent's internal state (e.g., after navigation changes the active contract).
     */
    public void reset() {
        state = new AgentState.Idle();
    }

    // --- Delete by name ---

    private AgentResult handleDelete(String name, ContractProfile profile) {
        List<Map<String, String>> items = parseItems(profile.description());
        for (Map<String, String> item : items) {
            if (matchesName(item, name)) {
                String id = item.get("id");
                if (id != null) {
                    Set<String> ids = Set.of(id);
                    return new AgentResult.IntentResult(
                        new AgentIntent("delete", Map.of("payload", ids)));
                }
            }
        }
        return new AgentResult.TextReply("Item '" + name + "' not found on the current page.");
    }

    private boolean matchesName(Map<String, String> item, String name) {
        // Check title, name, or label fields
        for (String key : List.of("title", "name", "label")) {
            String value = item.get(key);
            if (value != null && value.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // --- Edit selected ---

    private AgentResult handleEditSelected(ContractProfile profile) {
        if (!profile.isList()) {
            return new AgentResult.TextReply("No list contract active — nothing selected.");
        }
        // Emit edit intent without payload — the caller (e.g. PromptContract)
        // enriches it with the current selection from the list view.
        return new AgentResult.IntentResult(new AgentIntent("edit", Map.of()));
    }

    // --- Search/filter ---

    private AgentResult handleSearch(String field, String operator, String value,
                                     ContractProfile profile) {
        List<Map<String, String>> items = parseItems(profile.description());
        List<Map<String, String>> matches = new ArrayList<>();

        for (Map<String, String> item : items) {
            String fieldValue = item.get(field);
            if (fieldValue != null && compareValues(fieldValue, operator, value)) {
                matches.add(item);
            }
        }

        if (matches.isEmpty()) {
            return new AgentResult.TextReply(
                "No items found matching '" + field + " " + operator + " " + value + "'.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(matches.size()).append(" item(s) matching '")
          .append(field).append(" ").append(operator).append(" ").append(value).append("':\n");
        for (Map<String, String> match : matches) {
            sb.append("  - ").append(formatItem(match)).append("\n");
        }
        return new AgentResult.TextReply(sb.toString().trim());
    }

    private boolean compareValues(String fieldValue, String operator, String value) {
        try {
            // Try numeric comparison
            double fv = Double.parseDouble(fieldValue);
            double v = Double.parseDouble(value);
            return switch (operator) {
                case "<" -> fv < v;
                case "<=" -> fv <= v;
                case ">" -> fv > v;
                case ">=" -> fv >= v;
                case "=", "==" -> fv == v;
                case "!=", "<>" -> fv != v;
                default -> false;
            };
        } catch (NumberFormatException e) {
            // Fall back to string comparison
            int cmp = fieldValue.compareToIgnoreCase(value);
            return switch (operator) {
                case "=" , "==" -> cmp == 0;
                case "!=", "<>" -> cmp != 0;
                case "<" -> cmp < 0;
                case ">" -> cmp > 0;
                default -> false;
            };
        }
    }

    // --- Update (two-step) ---

    private AgentResult handleUpdate(String id, String modification, ContractProfile profile) {
        // Step 1: emit edit intent, remember what to modify
        state = new AgentState.PendingSave(modification);
        return new AgentResult.IntentResult(
            new AgentIntent("edit", Map.of("payload", id)));
    }

    private AgentResult handlePendingSave(String modification, ContractProfile profile) {
        if (!profile.isEdit() && !profile.isForm()) {
            return new AgentResult.TextReply(
                "Expected edit form to be active, but current contract is not a form.");
        }

        // Parse entity from edit contract's agentDescription
        String description = profile.description();
        if (description == null) {
            return new AgentResult.TextReply("Cannot read entity from the active contract.");
        }

        // Extract entity fields from "Entity: TypeName[field1=val1, field2=val2]"
        Map<String, Object> fieldValues = parseEntityFields(description);
        if (fieldValues.isEmpty()) {
            return new AgentResult.TextReply("Cannot parse entity fields from description.");
        }

        // Apply modification: append text to the first text-like field
        // (content > title > first string field)
        String targetField = findTextFieldForModification(fieldValues);
        if (targetField != null) {
            Object current = fieldValues.get(targetField);
            fieldValues.put(targetField, current + " " + modification);
        } else {
            return new AgentResult.TextReply(
                "Cannot determine which field to modify. Fields: " + fieldValues.keySet());
        }

        return new AgentResult.IntentResult(
            new AgentIntent("save", Map.of("payload", fieldValues)));
    }

    private String findTextFieldForModification(Map<String, Object> fields) {
        // Prefer content, then title, then first non-id string field
        if (fields.containsKey("content")) return "content";
        if (fields.containsKey("title")) return "title";
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!"id".equals(entry.getKey()) && entry.getValue() instanceof String) {
                return entry.getKey();
            }
        }
        return null;
    }

    // --- Navigation ---

    private AgentResult handleNavigate(String target, StructureNode structureTree) {
        // Search the structure tree for a matching label
        Class<? extends ViewContract> contractClass = findContractByLabel(target.trim(), structureTree);
        if (contractClass != null) {
            return new AgentResult.IntentResult(
                new AgentIntent("navigate", Map.of(), contractClass));
        }
        return new AgentResult.TextReply("No contract found matching '" + target + "'.");
    }

    @SuppressWarnings("unchecked")
    private Class<? extends ViewContract> findContractByLabel(String label, StructureNode node) {
        // Check this node's label
        if (node.label() != null && node.label().equalsIgnoreCase(label)) {
            // Return the first contract class from this node
            if (!node.contracts().isEmpty()) {
                Class<?> cls = node.contracts().iterator().next();
                if (ViewContract.class.isAssignableFrom(cls)) {
                    return (Class<? extends ViewContract>) cls;
                }
            }
        }
        // Recurse into children
        for (StructureNode child : node.children()) {
            Class<? extends ViewContract> found = findContractByLabel(label, child);
            if (found != null) return found;
        }
        return null;
    }

    // --- Parsing helpers ---

    /**
     * Parse item data from the agentDescription() "Items:" section.
     * Expected format: {@code TypeName[field1=val1, field2=val2]}
     */
    List<Map<String, String>> parseItems(String description) {
        if (description == null) return List.of();

        List<Map<String, String>> result = new ArrayList<>();
        boolean inItems = false;
        for (String line : description.split("\n")) {
            if (line.trim().startsWith("Items:")) {
                inItems = true;
                continue;
            }
            if (inItems) {
                if (!line.startsWith(" ") && !line.startsWith("\t")) {
                    break; // End of items section
                }
                Matcher m = ITEM_LINE_PATTERN.matcher(line);
                if (m.find()) {
                    result.add(parseRecordFields(m.group(1)));
                }
            }
        }
        return result;
    }

    /**
     * Parse "field1=val1, field2=val2" from inside a record toString().
     */
    private Map<String, String> parseRecordFields(String fieldsStr) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : fieldsStr.split(",\\s*")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                fields.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return fields;
    }

    /**
     * Parse entity fields from EditViewContract's agentDescription().
     * Expected: "Entity: TypeName[field1=val1, field2=val2]"
     */
    Map<String, Object> parseEntityFields(String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String line : description.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Entity:")) {
                String entity = trimmed.substring("Entity:".length()).trim();
                Matcher m = ITEM_LINE_PATTERN.matcher(entity);
                if (m.find()) {
                    for (Map.Entry<String, String> e : parseRecordFields(m.group(1)).entrySet()) {
                        result.put(e.getKey(), (Object) e.getValue());
                    }
                }
                break;
            }
        }
        return result;
    }

    private String formatItem(Map<String, String> item) {
        String name = item.getOrDefault("title",
                       item.getOrDefault("name",
                       item.getOrDefault("label", "?")));
        String id = item.getOrDefault("id", "?");
        return name + " (id: " + id + ")";
    }
}
