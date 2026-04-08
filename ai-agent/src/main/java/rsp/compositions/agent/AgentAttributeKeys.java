package rsp.compositions.agent;

/**
 * Agent-specific attribute keys for ABAC policy evaluation.
 * <p>
 * These extend the general {@link rsp.compositions.authorization.AttributeKeys}
 * with keys specific to agent delegation.
 */
public final class AgentAttributeKeys {
    private AgentAttributeKeys() {}

    public static final String SUBJECT_AGENT_ID = "subject.agent_id";
    public static final String SUBJECT_DELEGATION_GRANT_ID = "subject.delegation_grant_id";
    public static final String SUBJECT_DELEGATED_BY_USER_ID = "subject.delegated_by_user_id";
}
