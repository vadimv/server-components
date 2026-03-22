package rsp.compositions.authorization;

/**
 * Well-known attribute keys for ABAC policy evaluation.
 * <p>
 * Organized by namespace. Contracts provide {@code resource.*} and {@code action.*};
 * auth/session/runtime layers provide {@code subject.*}, {@code context.*}, and {@code grant.*}.
 */
public final class AttributeKeys {
    private AttributeKeys() {}

    // --- subject.* (who acts) ---
    public static final String SUBJECT_TYPE = "subject.type";
    public static final String SUBJECT_USER_ID = "subject.user_id";
    public static final String SUBJECT_ROLES = "subject.roles";

    // --- resource.* (what is targeted) ---
    public static final String RESOURCE_KIND = "resource.kind";
    public static final String RESOURCE_CONTRACT_CLASS = "resource.contract_class";
    public static final String RESOURCE_ENTITY_TYPE = "resource.entity_type";
    public static final String RESOURCE_ENTITY_ID = "resource.entity_id";
    public static final String RESOURCE_DOMAIN = "resource.domain";
    public static final String RESOURCE_SENSITIVITY = "resource.sensitivity";

    // --- action.* (what is attempted) ---
    public static final String ACTION_NAME = "action.name";
    public static final String ACTION_TYPE = "action.type";
    public static final String ACTION_RISK = "action.risk";
    public static final String ACTION_REQUIRES_CONFIRMATION = "action.requires_confirmation";

    // --- control.* (how it is performed) ---
    public static final String CONTROL_MODE = "control.mode";
    public static final String CONTROL_CHANNEL = "control.channel";
    public static final String CONTROL_USER_PRESENT = "control.user_present";

    // --- context.* (runtime constraints) ---
    public static final String CONTEXT_SESSION_ID = "context.session_id";
    public static final String CONTEXT_TENANT_ID = "context.tenant_id";
    public static final String CONTEXT_TIME = "context.time";
    public static final String CONTEXT_DEVICE_TRUST = "context.device_trust";
    public static final String CONTEXT_ENVIRONMENT = "context.environment";
    public static final String CONTEXT_RATE_LIMIT_BUCKET = "context.rate_limit_bucket";

    // --- grant.* (delegation envelope) ---
    public static final String GRANT_SCOPE_RESOURCES = "grant.scope.resources";
    public static final String GRANT_SCOPE_ACTIONS = "grant.scope.actions";
    public static final String GRANT_SCOPE_CONTROL_MODES = "grant.scope.control_modes";
    public static final String GRANT_EXPIRES_AT = "grant.expires_at";
    public static final String GRANT_REVOKED = "grant.revoked";
    public static final String GRANT_CONFIRMATION_POLICY = "grant.confirmation_policy";
}
