package rsp.app.posts.components;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptViewStateTests {

    @Nested
    class MessageDeduplication {

        @Test
        void withMessage_adds_new_message() {
            var state = new PromptView.PromptViewState(List.of());
            var msg = new PromptContract.Message(1, "hello", true);

            var updated = state.withMessage(msg);

            assertEquals(1, updated.messages().size());
            assertEquals("hello", updated.messages().get(0).text());
        }

        @Test
        void withMessage_rejects_duplicate_id() {
            var msg1 = new PromptContract.Message(1, "first", false);
            var state = new PromptView.PromptViewState(List.of(msg1));

            var msg2 = new PromptContract.Message(1, "duplicate", false);
            var updated = state.withMessage(msg2);

            assertSame(state, updated, "Should return same instance when message is duplicate");
            assertEquals(1, updated.messages().size());
            assertEquals("first", updated.messages().get(0).text());
        }

        @Test
        void withMessage_allows_different_ids() {
            var msg1 = new PromptContract.Message(1, "first", true);
            var state = new PromptView.PromptViewState(List.of(msg1));

            var msg2 = new PromptContract.Message(2, "second", false);
            var updated = state.withMessage(msg2);

            assertEquals(2, updated.messages().size());
        }

        @Test
        void withMessage_preserves_order_of_existing_messages() {
            var msg1 = new PromptContract.Message(1, "cmd", true);
            var msg2 = new PromptContract.Message(2, "reply", false);
            var state = new PromptView.PromptViewState(List.of(msg1, msg2));

            var msg3 = new PromptContract.Message(3, "cmd2", true);
            var updated = state.withMessage(msg3);

            assertEquals(3, updated.messages().size());
            assertEquals("cmd", updated.messages().get(0).text());
            assertEquals("reply", updated.messages().get(1).text());
            assertEquals("cmd2", updated.messages().get(2).text());
        }
    }

    @Nested
    class UpdateLastSystemMessage {

        @Test
        void replaces_last_system_message_text() {
            var state = new PromptView.PromptViewState(List.of(
                    new PromptContract.Message(1, "cmd", true),
                    new PromptContract.Message(2, "Thinking...", false)
            ));

            var updated = state.withLastSystemMessageUpdated("Done.");

            assertEquals(2, updated.messages().size());
            assertEquals("cmd", updated.messages().get(0).text());
            assertEquals("Done.", updated.messages().get(1).text());
        }

        @Test
        void preserves_message_id_on_update() {
            var state = new PromptView.PromptViewState(List.of(
                    new PromptContract.Message(5, "original", false)
            ));

            var updated = state.withLastSystemMessageUpdated("replaced");

            assertEquals(5, updated.messages().get(0).id());
        }

        @Test
        void skips_user_messages_to_find_last_system() {
            var state = new PromptView.PromptViewState(List.of(
                    new PromptContract.Message(1, "system1", false),
                    new PromptContract.Message(2, "user1", true),
                    new PromptContract.Message(3, "system2", false),
                    new PromptContract.Message(4, "user2", true)
            ));

            var updated = state.withLastSystemMessageUpdated("updated");

            assertEquals("system1", updated.messages().get(0).text());
            assertEquals("updated", updated.messages().get(2).text());
            assertEquals("user2", updated.messages().get(3).text());
        }
    }

    @Nested
    class Ordering {

        @Test
        void simulated_prompt_reply_navigate_flow_preserves_order() {
            // Simulate: user sends command, gets reply, then navigation triggers remount
            // On remount, history is loaded, then incremental events arrive

            // Step 1: Initial history loaded on mount
            var history = List.of(
                    new PromptContract.Message(1, "show posts", true),
                    new PromptContract.Message(2, "Thinking...", false),
                    new PromptContract.Message(3, "Navigating...", false)
            );
            var state = new PromptView.PromptViewState(history);

            // Step 2: NEW_MESSAGE arrives for a message already in history (dedup)
            var duplicate = new PromptContract.Message(3, "Navigating...", false);
            var updated = state.withMessage(duplicate);
            assertSame(state, updated, "Duplicate should be rejected");

            // Step 3: Genuinely new message arrives
            var newReply = new PromptContract.Message(4, "Done!", false);
            updated = state.withMessage(newReply);
            assertEquals(4, updated.messages().size());

            // Verify chronological order maintained
            assertEquals("show posts", updated.messages().get(0).text());
            assertEquals("Thinking...", updated.messages().get(1).text());
            assertEquals("Navigating...", updated.messages().get(2).text());
            assertEquals("Done!", updated.messages().get(3).text());
        }
    }
}
