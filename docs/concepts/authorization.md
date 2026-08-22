# Authorization

The `authorization` module is a small attribute-based access control (ABAC)
engine. A policy receives one immutable `Attributes` value and returns either
`AccessDecision.Allow` or `AccessDecision.Deny` with a reason.

Authorization is distinct from authentication:

- `system/compositions` contains login providers and contract-mount access
  strategies.
- `system/authorization` evaluates subject, resource, action, control, context,
  and delegation attributes.
- `extensions/ai-agent` adapts these decisions to agent discovery, execution,
  and approval flows.

## Define And Evaluate A Policy

Use the constants in `AttributeKeys` so producers and policies agree on names:

```java
import rsp.compositions.authorization.*;

import java.util.Set;

import static rsp.compositions.authorization.AttributeKeys.*;

AccessPolicy policy = attributes -> {
    if (!attributes.hasKey(SUBJECT_USER_ID)) {
        return new AccessDecision.Deny("User not authenticated");
    }

    Set<?> roles = attributes.getTyped(SUBJECT_ROLES, Set.class);
    boolean deleting = "delete".equals(attributes.getString(ACTION_NAME));
    if (deleting && (roles == null || !roles.contains("admin"))) {
        return new AccessDecision.Deny("Delete requires the admin role");
    }
    return new AccessDecision.Allow();
};

Authorization authorization = new Authorization(
        policy,
        Attributes.builder()
                .put(SUBJECT_USER_ID, "alice")
                .put(SUBJECT_ROLES, Set.of("admin"))
                .build());

AccessDecision decision = authorization.evaluate(
        Attributes.builder()
                .put(ACTION_NAME, "delete")
                .put(RESOURCE_KIND, "post")
                .put(RESOURCE_ENTITY_ID, "42")
                .build());
```

`Authorization` binds stable subject attributes once. Each call to `evaluate`
adds the attributes for the current action and resource. Keep values in their
documented namespaces; later values override earlier values with the same key.

## Compose Policies

`CompositePolicy` evaluates policies in order. The first denial wins; it allows
only when every policy allows.

```java
AccessPolicy policy = new CompositePolicy(
        ExamplePolicies.requireAuthenticated(),
        ExamplePolicies.readOnly());
```

`ExamplePolicies` contains reusable examples for authentication, read-only
access, and delegation constraints. `allowAll()` and an empty
`CompositePolicy` allow everything and should not be used as production
defaults.

## Delegation

`Authorization.delegated(grant)` derives an authorization context containing a
`DelegationGrant`'s scope, expiry, and revocation attributes.
`Authorization.scoped(actions)` is the smaller option when trusted code only
needs to restrict an existing context to named action entitlements.

The policy engine itself deliberately returns only allow or deny. User
confirmation is handled by agent gate and approval adapters, not by
`AccessPolicy`.

## Integration Boundaries

- Contract mounting calls `Contract.isAuthorized(Lookup)` before exposing a
  contract's state or view; see [compositions](compositions.md).
- Agent action filtering and approval use the same authorization data; see
  [AI agent integration](agent-integration.md).
- The complete runnable wiring is in
  [CrudApp.java](../../examples/src/main/java/rsp/app/posts/CrudApp.java).
