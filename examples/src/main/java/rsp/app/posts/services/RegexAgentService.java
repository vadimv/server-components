package rsp.app.posts.services;

import rsp.compositions.contract.ContractAction;
import rsp.compositions.contract.ContractActionPayload;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.ContractProfile;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based agent service — test/demo substitute for an LLM.
 * <p>
 * Parses natural-language prompts into agent results using simple regex matching.
 * Supports CRUD operations (delete, search, update), pagination, selection,
 * and navigation.
 */
public class RegexAgentService extends AgentService {

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

    private AgentState state = new AgentState.Idle();

    @Override
    public AgentResult handlePrompt(String prompt,
                                    ContractProfile profile,
                                    StructureNode structureTree) {
        // Detect compound commands (e.g. "show comments and go to page 2")
        List<String> parts = Arrays.stream(prompt.split("\\b(?:and then|then|and)\\b"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (parts.size() > 1) {
            return new AgentResult.PlanResult(parts, "Executing " + parts.size() + " steps");
        }

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
            return findActionResult("select_all", ContractActionPayload.EMPTY, profile);
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
            return findActionResult("page", ContractActionPayload.of(page), profile);
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

    // --- Action lookup helper ---

    private AgentResult findActionResult(String actionName, ContractActionPayload payload, ContractProfile profile) {
        for (ContractAction action : profile.actions()) {
            if (action.action().equals(actionName)) {
                return new AgentResult.ActionResult(action, payload);
            }
        }
        return new AgentResult.TextReply("Action '" + actionName + "' not available.");
    }

    // --- Delete by name ---

    private AgentResult handleDelete(String name, ContractProfile profile) {
        List<Map<String, Object>> items = extractItems(profile);
        for (Map<String, Object> item : items) {
            if (matchesName(item, name)) {
                Object id = item.get("id");
                if (id != null) {
                    ContractActionPayload payload = new ContractActionPayload(
                        new JsonDataType.Array(new JsonDataType.String(String.valueOf(id))));
                    return findActionResult("delete", payload, profile);
                }
            }
        }
        return new AgentResult.TextReply("Item '" + name + "' not found on the current page.");
    }

    private boolean matchesName(Map<String, Object> item, String name) {
        for (String key : List.of("title", "name", "label")) {
            Object value = item.get(key);
            if (value != null && String.valueOf(value).equalsIgnoreCase(name)) {
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
        return findActionResult("edit", ContractActionPayload.EMPTY, profile);
    }

    // --- Search/filter ---

    private AgentResult handleSearch(String field, String operator, String value,
                                     ContractProfile profile) {
        List<Map<String, Object>> items = extractItems(profile);
        List<Map<String, Object>> matches = new ArrayList<>();

        for (Map<String, Object> item : items) {
            Object fieldValue = item.get(field);
            if (fieldValue != null && compareValues(String.valueOf(fieldValue), operator, value)) {
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
        for (Map<String, Object> match : matches) {
            sb.append("  - ").append(formatItem(match)).append("\n");
        }
        return new AgentResult.TextReply(sb.toString().trim());
    }

    private boolean compareValues(String fieldValue, String operator, String value) {
        try {
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
        state = new AgentState.PendingSave(modification);
        return findActionResult("edit", ContractActionPayload.of(id), profile);
    }

    private AgentResult handlePendingSave(String modification, ContractProfile profile) {
        if (!profile.isEdit() && !profile.isForm()) {
            return new AgentResult.TextReply(
                "Expected edit form to be active, but current contract is not a form.");
        }

        Map<String, Object> fieldValues = new LinkedHashMap<>(extractEntity(profile));
        if (fieldValues.isEmpty()) {
            return new AgentResult.TextReply("Cannot read entity from the active contract.");
        }

        String targetField = findTextFieldForModification(fieldValues);
        if (targetField != null) {
            Object current = fieldValues.get(targetField);
            fieldValues.put(targetField, current + " " + modification);
        } else {
            return new AgentResult.TextReply(
                "Cannot determine which field to modify. Fields: " + fieldValues.keySet());
        }

        return findActionResult("save", toAgentPayload(fieldValues), profile);
    }

    private String findTextFieldForModification(Map<String, Object> fields) {
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
        Class<? extends ViewContract> contractClass = findContractByLabel(target.trim(), structureTree);
        if (contractClass != null) {
            return new AgentResult.NavigateResult(contractClass);
        }
        return new AgentResult.TextReply("No contract found matching '" + target + "'.");
    }

    @SuppressWarnings("unchecked")
    private Class<? extends ViewContract> findContractByLabel(String label, StructureNode node) {
        if (node.label() != null && node.label().equalsIgnoreCase(label)) {
            if (!node.contracts().isEmpty()) {
                Class<?> cls = node.contracts().iterator().next();
                if (ViewContract.class.isAssignableFrom(cls)) {
                    return (Class<? extends ViewContract>) cls;
                }
            }
        }
        for (StructureNode child : node.children()) {
            Class<? extends ViewContract> found = findContractByLabel(label, child);
            if (found != null) return found;
        }
        return null;
    }

    // --- Metadata extraction helpers ---

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(ContractProfile profile) {
        if (profile.metadata() != null
                && profile.metadata().state().get("items") instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractEntity(ContractProfile profile) {
        if (profile.metadata() != null
                && profile.metadata().state().get("entity") instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String formatItem(Map<String, Object> item) {
        String name = String.valueOf(item.getOrDefault("title",
                       item.getOrDefault("name",
                       item.getOrDefault("label", "?"))));
        String id = String.valueOf(item.getOrDefault("id", "?"));
        return name + " (id: " + id + ")";
    }

    // --- Java value to AgentPayload conversion ---

    private static ContractActionPayload toAgentPayload(Map<String, Object> map) {
        Map<String, JsonDataType> entries = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            entries.put(e.getKey(), toJsonDataType(e.getValue()));
        }
        return new ContractActionPayload(new JsonDataType.Object(entries));
    }

    private static JsonDataType toJsonDataType(Object value) {
        if (value == null) return JsonDataType.Null.INSTANCE;
        if (value instanceof String s) return new JsonDataType.String(s);
        if (value instanceof Integer i) return JsonDataType.Number.of(i);
        if (value instanceof Long l) return JsonDataType.Number.of(l);
        if (value instanceof Double d) return JsonDataType.Number.of(d);
        if (value instanceof Boolean b) return new JsonDataType.Boolean(b);
        return new JsonDataType.String(value.toString());
    }
}
