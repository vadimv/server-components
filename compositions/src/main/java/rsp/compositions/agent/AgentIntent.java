package rsp.compositions.agent;

import rsp.compositions.contract.ViewContract;

import java.util.Map;

/**
 * The agent's output — pure data, never an action.
 * <p>
 * The agent produces intents from user prompts. An intent describes
 * what the user wants to do, but does not execute it directly.
 * The {@link IntentGate} evaluates rules, and only then does the
 * dispatcher publish framework events.
 *
 * @param action         the action name (e.g., "navigate", "page", "select_all", "edit", "create")
 * @param params         action parameters (e.g., {page: 3} or {id: "5"})
 * @param targetContract the target contract class (for navigation), or null
 */
public record AgentIntent(String action,
                          Map<String, Object> params,
                          Class<? extends ViewContract> targetContract) {

    public AgentIntent {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be null or blank");
        }
        params = params != null ? Map.copyOf(params) : Map.of();
    }

    public AgentIntent(String action) {
        this(action, Map.of(), null);
    }

    public AgentIntent(String action, Map<String, Object> params) {
        this(action, params, null);
    }
}
