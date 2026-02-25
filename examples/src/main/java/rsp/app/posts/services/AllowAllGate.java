package rsp.app.posts.services;

import rsp.component.Lookup;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.IntentGate;

/**
 * Prototype gate that allows all intents.
 */
public class AllowAllGate implements IntentGate {
    @Override
    public GateResult evaluate(AgentIntent intent, Lookup lookup) {
        return new GateResult.Allow(intent);
    }
}
