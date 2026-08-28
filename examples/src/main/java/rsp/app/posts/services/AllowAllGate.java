package rsp.app.posts.services;

import rsp.component.Lookup;
import rsp.compositions.block.BlockAction;
import rsp.compositions.block.BlockActionPayload;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.ActionGate;

/**
 * Prototype gate that allows all actions.
 */
public class AllowAllGate implements ActionGate {
    @Override
    public GateResult evaluate(BlockAction action, BlockActionPayload payload, Lookup lookup) {
        return new GateResult.Allow(action, payload);
    }
}
