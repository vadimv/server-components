package rsp.compositions.agent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link DelegationStore} backed by {@link ConcurrentHashMap}.
 * <p>
 * Thread-safe. Suitable for single-node deployments and examples.
 */
public final class InMemoryDelegationStore implements DelegationStore {
    private final ConcurrentMap<String, Decision> decisions = new ConcurrentHashMap<>();

    @Override
    public void recordDecision(String sessionKey, boolean approved) {
        decisions.put(sessionKey, new Decision(approved, sessionKey));
    }

    @Override
    public Decision getDecision(String sessionKey) {
        return decisions.get(sessionKey);
    }

    @Override
    public void removeDecision(String sessionKey) {
        decisions.remove(sessionKey);
    }
}
