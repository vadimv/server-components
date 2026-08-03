package rsp.compositions.agentui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PromptViewTests {

    @Test
    void duplicate_messages_are_not_added_twice() {
        PromptContract.Message message = new PromptContract.Message(1, "reply", false);
        PromptView.PromptViewState state = new PromptView.PromptViewState(List.of(message), "Posts");

        PromptView.PromptViewState updated = state.withMessage(message);

        assertSame(state, updated);
    }

    @Test
    void optimistic_messages_get_distinct_local_ids() {
        PromptView.PromptViewState state = new PromptView.PromptViewState(List.of(), "Posts");

        PromptView.PromptViewState updated = state.withOptimisticMessage("first")
                .withOptimisticMessage("second");

        assertEquals(List.of(-1L, -2L), updated.messages().stream().map(PromptContract.Message::id).toList());
    }

    @Test
    void active_category_changes_preserve_the_message_cache() {
        PromptContract.Message message = new PromptContract.Message(1, "reply", false);
        PromptView.PromptViewState state = new PromptView.PromptViewState(List.of(message), "Posts");

        PromptView.PromptViewState updated = state.withActiveCategory("Comments");

        assertEquals("Comments", updated.activeCategory());
        assertEquals(List.of(message), updated.messages());
    }
}
