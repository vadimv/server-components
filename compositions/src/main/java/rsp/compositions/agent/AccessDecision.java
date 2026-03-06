package rsp.compositions.agent;

/**
 * Result of an {@link AccessPolicy#evaluate} call.
 * <p>
 * Binary outcome: Allow or Deny. Confirmation is a UI concern
 * handled by {@link IntentGate} adapters, not the policy engine.
 */
public sealed interface AccessDecision {
    record Allow() implements AccessDecision {}
    record Deny(String reason) implements AccessDecision {}
}
