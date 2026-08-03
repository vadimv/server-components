package rsp.compositions.agentui;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.EventKey;
import rsp.component.StateUpdater;
import rsp.compositions.agent.DelegationStore;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ContractNodeComponent;
import rsp.page.QualifiedSessionId;

import java.util.Map;
import java.util.Objects;

/**
 * Standardized approval dialog for agent delegation requests.
 * <p>
 * Shown as a modal overlay when {@link rsp.compositions.agent.AgentSpawner} returns
 * {@link rsp.compositions.agent.SpawnResult.RequiresApproval}. Displays scope, control mode,
 * and purpose. User clicks Approve or Deny. Decision is saved to
 * {@link DelegationStore} and {@link #APPROVAL_DECIDED} event is emitted.
 */
public class DelegationApprovalContract
        extends ContractNodeComponent<DelegationApprovalView.ApprovalViewState, DelegationApprovalView.Decision> {

    /** Emitted when user decides. Payload: {@code true}=approved, {@code false}=denied. */
    public static final EventKey.SimpleKey<Boolean> APPROVAL_DECIDED =
            new EventKey.SimpleKey<>("delegation.approval.decided", Boolean.class);

    private final DelegationStore store;

    public DelegationApprovalContract(DelegationStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public ComponentStateSupplier<DelegationApprovalView.ApprovalViewState> initStateSupplier() {
        return (_, context) -> {
            Map<String, Object> showData = context.get(ContextKeys.SHOW_DATA);
            String scope = showData == null ? "APP" : String.valueOf(showData.getOrDefault("scope", "APP"));
            String controlMode = showData == null
                    ? "ASSIST"
                    : String.valueOf(showData.getOrDefault("controlMode", "ASSIST"));
            String reason = showData == null ? "" : String.valueOf(showData.getOrDefault("reason", ""));
            return new DelegationApprovalView.ApprovalViewState(scope, controlMode, reason);
        };
    }

    @Override
    public ComponentView<DelegationApprovalView.ApprovalViewState, DelegationApprovalView.Decision> componentView() {
        return new DelegationApprovalView();
    }

    @Override
    protected void onIntent(DelegationApprovalView.Decision intent,
                            DelegationApprovalView.ApprovalViewState state,
                            StateUpdater<DelegationApprovalView.ApprovalViewState> stateUpdater) {
            QualifiedSessionId qsid = lookup().get(QualifiedSessionId.class);
            String sessionKey = qsid != null ? qsid.sessionId() : "unknown-session";
            store.recordDecision(sessionKey, intent.approved());

            lookup().publish(APPROVAL_DECIDED, intent.approved());
            lookup().publish(EventKeys.HIDE, DelegationApprovalContract.class);
    }

    @Override
    public String title() {
        return "Agent Delegation Approval";
    }

}
