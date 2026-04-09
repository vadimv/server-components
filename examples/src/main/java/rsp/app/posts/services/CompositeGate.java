package rsp.app.posts.services;

import rsp.component.Lookup;
import rsp.compositions.contract.AgentAction;
import rsp.compositions.contract.AgentPayload;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.ActionGate;

import java.util.List;

/**
 * Composite gate that chains multiple rules.
 * First block or confirm wins; otherwise allows.
 */
public class CompositeGate implements ActionGate {
    private final List<ActionGate> rules;

    public CompositeGate(List<ActionGate> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public GateResult evaluate(AgentAction action, AgentPayload payload, Lookup lookup) {
        for (ActionGate rule : rules) {
            GateResult result = rule.evaluate(action, payload, lookup);
            if (result instanceof GateResult.Block || result instanceof GateResult.Confirm) {
                return result;
            }
        }
        return new GateResult.Allow(action, payload);
    }
}
