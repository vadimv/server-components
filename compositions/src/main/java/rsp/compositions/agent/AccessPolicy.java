package rsp.compositions.agent;

/**
 * Unified ABAC policy interface for all authorization decisions.
 * <p>
 * One policy engine governs agent spawn ({@code canSpawn}), agent discovery
 * ({@code canDiscover}), and agent execution ({@code canExecute}).
 * The same policy can also govern human UI authorization.
 * <p>
 * Implementations evaluate attributes from typed namespaces
 * ({@code subject.*}, {@code resource.*}, {@code action.*}, {@code control.*},
 * {@code context.*}, {@code grant.*}) and return an {@link AccessDecision}.
 *
 * @see AttributeKeys
 * @see Attributes
 */
@FunctionalInterface
public interface AccessPolicy {
    AccessDecision evaluate(Attributes attributes);
}
