package rsp.app.posts.services;

import rsp.component.Lookup;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.IntentGate;

import java.util.List;

/**
 * Composite gate that chains multiple rules.
 * First block or confirm wins; otherwise allows.
 */
public class CompositeGate implements IntentGate {
    private final List<IntentGate> rules;

    public CompositeGate(List<IntentGate> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public GateResult evaluate(AgentIntent intent, Lookup lookup) {
        for (IntentGate rule : rules) {
            GateResult result = rule.evaluate(intent, lookup);
            if (result instanceof GateResult.Block || result instanceof GateResult.Confirm) {
                return result;
            }
        }
        return new GateResult.Allow(intent);
    }
}
