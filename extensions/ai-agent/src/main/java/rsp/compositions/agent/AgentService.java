package rsp.compositions.agent;

import rsp.compositions.block.Block;
import rsp.compositions.block.BlockTarget;

import rsp.compositions.block.BlockActionPayload;


import rsp.compositions.block.BlockAction;

import rsp.compositions.composition.StructureNode;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base agent service — defines result types and the prompt-handling block.
 * <p>
 * The framework provides context (block metadata, declared actions, structure tree)
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
        /** A block action to be dispatched via ActionDispatcher. */
        record ActionResult(BlockAction action, BlockActionPayload payload) implements AgentResult {}
        /** A navigation request to switch the active block. */
        record NavigateResult(BlockTarget target) implements AgentResult {
            public NavigateResult {
                Objects.requireNonNull(target, "target");
            }

            public NavigateResult(Class<? extends Block<?, ?>> targetBlock) {
                this(new BlockTarget(targetBlock, targetBlock));
            }

            public Class<? extends Block<?, ?>> targetBlock() {
                return target.blockClass();
            }

            public Object targetKey() {
                return target.key();
            }
        }
        /** A text reply to show the user (no framework event). */
        record TextReply(String message) implements AgentResult {}
        /** A multi-step plan: each step is a natural-language intent to be executed sequentially. */
        record PlanResult(List<String> steps, String summary) implements AgentResult {}
    }

    /**
     * Process a user prompt against the active block's profile and structure tree.
     * <p>
     * Default implementation returns a text reply. Subclasses override to provide
     * actual prompt parsing (LLM-based or regex).
     *
     * @param prompt        the user's natural-language input
     * @param profile       the active block's profile (metadata + actions)
     * @param structureTree the navigation structure
     * @return the result (action, navigation, or text reply)
     */
    public AgentResult handlePrompt(String prompt,
                                    BlockProfile profile,
                                    StructureNode structureTree) {
        return new AgentResult.TextReply("Not implemented");
    }

    /**
     * Process a user prompt with streaming token callback.
     * Default implementation ignores the callback and delegates to the non-streaming version.
     * Subclasses (e.g., LLM-based services) override this for progressive token delivery.
     *
     * @param prompt           the user's natural-language input
     * @param profile          the active block's profile
     * @param structureTree    the navigation structure
     * @param onPartialContent called with accumulated content as tokens arrive
     * @return the result (action, navigation, or text reply)
     */
    public AgentResult handlePrompt(String prompt,
                                    BlockProfile profile,
                                    StructureNode structureTree,
                                    Consumer<String> onPartialContent) {
        return handlePrompt(prompt, profile, structureTree);
    }

    /**
     * Process a user prompt with streaming and a cooperative cancellation token.
     * Default implementation ignores the token and delegates to the 4-arg overload.
     * Subclasses that talk to a real LLM should check {@code abortToken.isCancelled()}
     * at streaming boundaries and abort the underlying HTTP call when cancelled.
     *
     * @param prompt           the user's natural-language input
     * @param profile          the active block's profile
     * @param structureTree    the navigation structure
     * @param onPartialContent called with accumulated content as tokens arrive
     * @param abortToken       cancellation signal — implementations may poll
     *                         and abort early when {@code isCancelled()} is true
     * @return the result (action, navigation, or text reply)
     */
    public AgentResult handlePrompt(String prompt,
                                    BlockProfile profile,
                                    StructureNode structureTree,
                                    Consumer<String> onPartialContent,
                                    AbortToken abortToken) {
        return handlePrompt(prompt, profile, structureTree, onPartialContent);
    }
}
