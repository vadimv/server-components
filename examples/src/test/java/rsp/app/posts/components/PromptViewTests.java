package rsp.app.posts.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptViewTests {

    private PromptService promptService;
    private static final String SCOPE = "test-session";

    @BeforeEach
    void setUp() {
        promptService = new PromptService();
    }

    private ComponentContext contextWithService() {
        return new ComponentContext()
                .with(PromptContextKeys.PROMPT_SERVICE, promptService)
                .with(PromptContextKeys.SCOPE_KEY, SCOPE);
    }

    @Nested
    class InitStateSupplier {

        @Test
        void loads_history_from_service() {
            promptService.sendPrompt(SCOPE, "cmd1");
            promptService.sendReply(SCOPE, "reply1");

            PromptView view = new PromptView();
            ComponentStateSupplier<PromptView.PromptViewState> supplier = view.initStateSupplier();
            PromptView.PromptViewState state = supplier.getState(null, contextWithService());

            assertEquals(2, state.messages().size());
            assertEquals("cmd1", state.messages().get(0).text());
            assertTrue(state.messages().get(0).fromUser());
            assertEquals("reply1", state.messages().get(1).text());
            assertFalse(state.messages().get(1).fromUser());
        }

        @Test
        void returns_empty_when_no_service_in_context() {
            PromptView view = new PromptView();
            ComponentStateSupplier<PromptView.PromptViewState> supplier = view.initStateSupplier();
            PromptView.PromptViewState state = supplier.getState(null, new ComponentContext());

            assertTrue(state.messages().isEmpty());
        }

        @Test
        void returns_empty_when_no_history() {
            PromptView view = new PromptView();
            ComponentStateSupplier<PromptView.PromptViewState> supplier = view.initStateSupplier();
            PromptView.PromptViewState state = supplier.getState(null, contextWithService());

            assertTrue(state.messages().isEmpty());
        }

        @Test
        void preserves_message_ids_from_service() {
            promptService.sendReply(SCOPE, "msg1");
            promptService.sendReply(SCOPE, "msg2");

            List<PromptService.Message> serviceHistory = promptService.getMessageHistory(SCOPE);
            long id1 = serviceHistory.get(0).id();
            long id2 = serviceHistory.get(1).id();

            PromptView view = new PromptView();
            PromptView.PromptViewState state = view.initStateSupplier().getState(null, contextWithService());

            assertEquals(id1, state.messages().get(0).id());
            assertEquals(id2, state.messages().get(1).id());
        }

        @Test
        void history_snapshot_is_independent_of_later_service_changes() {
            promptService.sendReply(SCOPE, "before");

            PromptView view = new PromptView();
            PromptView.PromptViewState state = view.initStateSupplier().getState(null, contextWithService());

            promptService.sendReply(SCOPE, "after");

            assertEquals(1, state.messages().size(),
                    "State should reflect history at init time, not later additions");
        }
    }

    @Nested
    class DedupIntegration {

        @Test
        void history_then_event_for_same_message_does_not_duplicate() {
            promptService.sendReply(SCOPE, "reply");

            PromptView view = new PromptView();
            PromptView.PromptViewState state = view.initStateSupplier().getState(null, contextWithService());
            assertEquals(1, state.messages().size());

            // Simulate NEW_MESSAGE event for same message (same ID)
            long existingId = state.messages().get(0).id();
            var eventMsg = new PromptContract.Message(existingId, "reply", false);
            PromptView.PromptViewState updated = state.withMessage(eventMsg);

            assertSame(state, updated, "Duplicate should be rejected");
            assertEquals(1, updated.messages().size());
        }

        @Test
        void history_then_new_event_appends_correctly() {
            promptService.sendReply(SCOPE, "old-reply");

            PromptView view = new PromptView();
            PromptView.PromptViewState state = view.initStateSupplier().getState(null, contextWithService());

            // Simulate NEW_MESSAGE for a genuinely new message
            var newMsg = new PromptContract.Message(999, "new-reply", false);
            PromptView.PromptViewState updated = state.withMessage(newMsg);

            assertEquals(2, updated.messages().size());
            assertEquals("old-reply", updated.messages().get(0).text());
            assertEquals("new-reply", updated.messages().get(1).text());
        }
    }
}
