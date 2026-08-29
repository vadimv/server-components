package rsp.compositions.agent;

import rsp.compositions.block.Block;

import rsp.compositions.block.BlockActionPayload;


import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.block.BlockAction;
import rsp.compositions.block.EventKeys;
import rsp.compositions.block.BlockRuntime;

import java.util.concurrent.CompletableFuture;

/**
 * The only component with publish access — translates allowed actions into framework events.
 * <p>
 * The dispatcher receives an {@link BlockAction} directly (no lookup by name needed)
 * and publishes the associated {@link EventKey} on the block's lookup.
 * <p>
 * Navigation is handled separately via {@link #dispatchNavigate}.
 */
public class ActionDispatcher {

    /**
     * Thread-local marker set while this dispatcher is publishing an
     * agent-originated event. Subscribers that want to distinguish
     * agent dispatches from user-driven events (e.g. the runtime's
     * user-interaction monitor) can consult {@link #isAgentDispatch()}.
     * <p>
     * The flag is set/cleared synchronously around each publish so a
     * handler running on the same thread observes {@code true}; once the
     * publish returns the flag is cleared, so subsequent thread reuse
     * does not see a stale value.
     */
    private static final ThreadLocal<Boolean> AGENT_DISPATCH =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * @return {@code true} if the current thread is inside an agent
     *         dispatch (i.e. {@code dispatch}/{@code dispatchDirect}/
     *         {@code dispatchNavigate} has set the marker and not yet cleared it)
     */
    public static boolean isAgentDispatch() {
        return AGENT_DISPATCH.get();
    }

    /**
     * Result of a dispatch attempt.
     */
    public sealed interface DispatchResult {
        record Dispatched(BlockAction action,
                          BlockActionPayload payload,
                          CompletableFuture<Void> processed) implements DispatchResult {}
        record Blocked(String reason) implements DispatchResult {}
        record AwaitingConfirmation(String question, BlockAction action, BlockActionPayload payload) implements DispatchResult {}
        record PayloadError(String action, String message) implements DispatchResult {}
    }

    /**
     * Evaluate action through the gate, then dispatch if allowed.
     *
     * @param action  the agent action to dispatch
     * @param payload the agent payload
     * @param block the active block
     * @param lookup  the current context (for gate evaluation)
     * @param gate    the rule engine
     * @return the dispatch result
     */
    public DispatchResult dispatch(BlockAction action, BlockActionPayload payload,
                                   BlockRuntime block, Lookup lookup, ActionGate gate) {
        AGENT_DISPATCH.set(Boolean.TRUE);
        try {
            GateResult result = gate.evaluate(action, payload, lookup);
            return switch (result) {
                case GateResult.Allow a -> publishEvent(a.action(), a.payload(), block);
                case GateResult.Block b -> new DispatchResult.Blocked(b.reason());
                case GateResult.Confirm c -> new DispatchResult.AwaitingConfirmation(c.question(), c.action(), c.payload());
            };
        } finally {
            AGENT_DISPATCH.set(Boolean.FALSE);
        }
    }

    /**
     * Dispatch an action directly (no gate evaluation).
     * Used after confirmation has been received.
     */
    public DispatchResult dispatchDirect(BlockAction action, BlockActionPayload payload, BlockRuntime block) {
        AGENT_DISPATCH.set(Boolean.TRUE);
        try {
            return publishEvent(action, payload, block);
        } finally {
            AGENT_DISPATCH.set(Boolean.FALSE);
        }
    }

    /**
     * Dispatch a navigation event to switch the active block.
     *
     * @param targetBlock the block class to navigate to
     * @param lookup         the current context (for event publishing)
     */
    public void dispatchNavigate(Class<? extends Block<?, ?>> targetBlock, Lookup lookup) {
        dispatchNavigate((Object) targetBlock, lookup);
    }

    /** Dispatch navigation to a configured block binding key. */
    public void dispatchNavigate(Object targetBlock, Lookup lookup) {
        AGENT_DISPATCH.set(Boolean.TRUE);
        try {
            lookup.publish(EventKeys.SET_PRIMARY, targetBlock);
        } finally {
            AGENT_DISPATCH.set(Boolean.FALSE);
        }
    }

    @SuppressWarnings("unchecked")
    private DispatchResult publishEvent(BlockAction action, BlockActionPayload payload, BlockRuntime block) {
        Lookup blockLookup = block.lookup();
        EventKey<?> key = action.eventKey();

        if (key instanceof EventKey.VoidKey vk) {
            blockLookup.publish(vk);
        } else if (key instanceof EventKey.SimpleKey<?> sk) {
            Object parsed;
            try {
                parsed = action.parsePayload().apply(payload);
            } catch (IllegalArgumentException e) {
                return new DispatchResult.PayloadError(action.action(), e.getMessage());
            }
            blockLookup.publish((EventKey.SimpleKey) sk, parsed);
        }

        // Enqueue a fence task after the action event — completes after the handler runs
        CompletableFuture<Void> processed = new CompletableFuture<>();
        blockLookup.enqueueTask(() -> processed.complete(null));
        return new DispatchResult.Dispatched(action, payload, processed);
    }
}
