package rsp.app.posts.services;

import rsp.component.Lookup;
import rsp.compositions.contract.ContractAction;
import rsp.compositions.contract.ContractActionPayload;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.ActionGate;

/**
 * Prototype gate that allows all actions.
 */
public class AllowAllGate implements ActionGate {
    @Override
    public GateResult evaluate(ContractAction action, ContractActionPayload payload, Lookup lookup) {
        return new GateResult.Allow(action, payload);
    }
}
