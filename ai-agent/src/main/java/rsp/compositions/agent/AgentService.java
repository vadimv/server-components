package rsp.compositions.agent;

import rsp.compositions.contract.AgentAction;

import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ViewContract;

import java.util.List;
import java.util.function.Consumer;

/**
 * Base agent service — defines result types and the prompt-handling contract.
 * <p>
 * The framework provides context (contract metadata, declared actions, structure tree)
 * but does not parse prompts. Parsing is the responsibility of app-level implementations
 * (LLM-based or regex demo substitutes).
 * <p>
 * Subclasses override {@link #handlePrompt} to produce results from user prompts.
 */
public class AgentService {

    /**
     * Result of processing a prompt.
     */
    public sealed interface AgentResult {
        /** A contract action to be dispatched via ActionDispatcher. */
        record ActionResult(AgentAction action, AgentPayload payload) implements AgentResult {}
        /** A navigation request to switch the active contract. */
        record NavigateResult(Class<? extends ViewContract> targetContract) implements AgentResult {}
        /** A text reply to show the user (no framework event). */
        record TextReply(String message) implements AgentResult {}
        /** A multi-step plan: each step is a natural-language intent to be executed sequentially. */
        record PlanResult(List<String> steps, String summary) implements AgentResult {}
    }

    /**
     * Process a user prompt against the active contract's profile and structure tree.
     * <p>
     * Default implementation returns a text reply. Subclasses override to provide
     * actual prompt parsing (LLM-based or regex).
     *
     * @param prompt        the user's natural-language input
     * @param profile       the active contract's profile (metadata + actions)
     * @param structureTree the navigation structure
     * @return the result (action, navigation, or text reply)
     */
    public AgentResult handlePrompt(String prompt,
                                    ContractProfile profile,
                                    StructureNode structureTree) {
        return new AgentResult.TextReply("Not implemented");
    }

    /**
     * Process a user prompt with streaming token callback.
     * Default implementation ignores the callback and delegates to the non-streaming version.
     * Subclasses (e.g., LLM-based services) override this for progressive token delivery.
     *
     * @param prompt           the user's natural-language input
     * @param profile          the active contract's profile
     * @param structureTree    the navigation structure
     * @param onPartialContent called with accumulated content as tokens arrive
     * @return the result (action, navigation, or text reply)
     */
    public AgentResult handlePrompt(String prompt,
                                    ContractProfile profile,
                                    StructureNode structureTree,
                                    Consumer<String> onPartialContent) {
        return handlePrompt(prompt, profile, structureTree);
    }
}
