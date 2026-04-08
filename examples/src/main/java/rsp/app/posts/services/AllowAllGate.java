package rsp.app.posts.services;

import rsp.component.Lookup;
import rsp.compositions.contract.AgentAction;
import rsp.compositions.agent.AgentPayload;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.ActionGate;

/**
 * Prototype gate that allows all actions.
 */
public class AllowAllGate implements ActionGate {
    @Override
    public GateResult evaluate(AgentAction action, AgentPayload payload, Lookup lookup) {
        return new GateResult.Allow(action, payload);
    }
}
