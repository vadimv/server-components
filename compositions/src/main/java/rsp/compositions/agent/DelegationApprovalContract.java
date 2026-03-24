package rsp.compositions.agent;

import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ViewContract;
import rsp.page.QualifiedSessionId;

import java.util.Map;
import java.util.Objects;

/**
 * Standardized approval dialog for agent delegation requests.
 * <p>
 * Shown as a modal overlay when {@link AgentSpawner} returns
 * {@link SpawnResult.RequiresApproval}. Displays scope, control mode,
 * and purpose. User clicks Approve or Deny. Decision is saved to
 * {@link DelegationStore} and {@link #APPROVAL_DECIDED} event is emitted.
 */
public class DelegationApprovalContract extends ViewContract {

    /** Emitted when user decides. Payload: {@code true}=approved, {@code false}=denied. */
    public static final EventKey.SimpleKey<Boolean> APPROVAL_DECIDED =
            new EventKey.SimpleKey<>("delegation.approval.decided", Boolean.class);

    /** Internal event from view buttons to contract. */
    public static final EventKey.SimpleKey<Boolean> USER_DECISION =
            new EventKey.SimpleKey<>("delegation.approval.userDecision", Boolean.class);

    /** Context key for scope label passed to view. */
    public static final ContextKey.StringKey<String> APPROVAL_SCOPE =
            new ContextKey.StringKey<>("approval.scope", String.class);

    /** Context key for control mode label passed to view. */
    public static final ContextKey.StringKey<String> APPROVAL_CONTROL_MODE =
            new ContextKey.StringKey<>("approval.controlMode", String.class);

    /** Context key for reason/purpose passed to view. */
    public static final ContextKey.StringKey<String> APPROVAL_REASON =
            new ContextKey.StringKey<>("approval.reason", String.class);

    private final DelegationStore store;
    private final String scopeLabel;
    private final String controlModeLabel;
    private final String reason;

    public DelegationApprovalContract(Lookup lookup, DelegationStore store) {
        super(lookup);
        this.store = Objects.requireNonNull(store);

        Map<String, Object> showData = lookup.get(ContextKeys.SHOW_DATA);
        this.scopeLabel = showData != null
                ? String.valueOf(showData.getOrDefault("scope", "APP")) : "APP";
        this.controlModeLabel = showData != null
                ? String.valueOf(showData.getOrDefault("controlMode", "ASSIST")) : "ASSIST";
        this.reason = showData != null
                ? String.valueOf(showData.getOrDefault("reason", "")) : "";

        subscribe(USER_DECISION, (eventName, approved) -> {
            QualifiedSessionId qsid = lookup.get(QualifiedSessionId.class);
            String sessionKey = qsid != null ? qsid.sessionId() : "unknown-session";
            store.recordDecision(sessionKey, approved);

            lookup.publish(APPROVAL_DECIDED, approved);
            lookup.publish(EventKeys.HIDE, DelegationApprovalContract.class);
        });
    }

    @Override
    public String title() {
        return "Agent Delegation Approval";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context
                .with(ContextKeys.CONTRACT_TITLE, title())
                .with(ContextKeys.OVERLAY_TITLE, title())
                .with(APPROVAL_SCOPE, scopeLabel)
                .with(APPROVAL_CONTROL_MODE, controlModeLabel)
                .with(APPROVAL_REASON, reason);
    }
}
