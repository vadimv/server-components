package rsp.compositions.agent;

import rsp.compositions.block.BlockAction;
import rsp.compositions.block.BlockMetadata;

import rsp.compositions.block.EditBlock;
import rsp.compositions.block.FormBlock;
import rsp.compositions.block.ListBlock;
import rsp.compositions.block.BlockRuntime;

import java.util.List;

/**
 * Profile of a block's capabilities for agent discovery.
 * <p>
 * Combines structured metadata from {@link BlockRuntime#blockMetadata()}
 * with the declared action vocabulary from {@link BlockRuntime#agentActions()}.
 * <p>
 * The agent receives the metadata for reasoning (live state, schema)
 * and the actions for intent construction (what can be done).
 *
 * @param metadata      structured metadata (nullable — block may not expose metadata)
 * @param actions       declared agent-invocable actions
 * @param blockClass the block's class
 */
public record BlockProfile(BlockMetadata metadata,
                              List<BlockAction> actions,
                              Class<?> blockClass) {

    /**
     * Build a profile from a block instance.
     *
     * @param block the block to profile
     * @return the profile
     */
    public static BlockProfile of(BlockRuntime block) {
        if (block == null) {
            return new BlockProfile(null, List.of(), Void.class);
        }

        BlockMetadata metadata = block.blockMetadata();
        List<BlockAction> actions = block.agentActions();

        return new BlockProfile(metadata, actions, block.getClass());
    }

    /**
     * Check if this profile represents a list block.
     */
    public boolean isList() {
        return ListBlock.class.isAssignableFrom(blockClass);
    }

    /**
     * Check if this profile represents an edit block.
     */
    public boolean isEdit() {
        return EditBlock.class.isAssignableFrom(blockClass);
    }

    /**
     * Check if this profile represents a form block (edit or create).
     */
    public boolean isForm() {
        return FormBlock.class.isAssignableFrom(blockClass);
    }
}
