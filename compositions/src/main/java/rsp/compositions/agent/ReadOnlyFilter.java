package rsp.compositions.agent;

import rsp.component.Lookup;

import java.util.List;
import java.util.Set;

/**
 * Action filter that allows only non-mutating actions.
 * <p>
 * A read-only agent sees pagination and selection but not create, edit, or delete.
 */
public class ReadOnlyFilter implements AgentActionFilter {
    private static final Set<String> ALLOWED = Set.of("page", "select_all");

    @Override
    public List<AgentAction> filter(List<AgentAction> actions, Lookup context) {
        return actions.stream()
            .filter(a -> ALLOWED.contains(a.action()))
            .toList();
    }
}
