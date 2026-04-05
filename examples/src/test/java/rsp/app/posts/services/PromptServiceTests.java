package rsp.app.posts.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptServiceTests {

    private PromptService service;
    private static final String SCOPE = "test-session";

    @BeforeEach
    void setUp() {
        service = new PromptService();
    }

    @Nested
    class MessageIds {

        @Test
        void each_message_gets_a_unique_id() {
            service.sendReply(SCOPE, "reply1");
            service.sendReply(SCOPE, "reply2");
            service.sendReply(SCOPE, "reply3");

            List<PromptService.Message> history = service.getMessageHistory(SCOPE);
            long distinctIds = history.stream().mapToLong(PromptService.Message::id).distinct().count();
            assertEquals(history.size(), distinctIds);
        }

        @Test
        void prompt_and_reply_ids_are_unique() {
            service.sendPrompt(SCOPE, "user input");
            service.sendReply(SCOPE, "agent reply");

            List<PromptService.Message> history = service.getMessageHistory(SCOPE);
            assertEquals(2, history.size());
            assertNotEquals(history.get(0).id(), history.get(1).id());
        }
    }

    @Nested
    class Ordering {

        @Test
        void history_preserves_insertion_order() {
            service.sendPrompt(SCOPE, "cmd1");
            service.sendReply(SCOPE, "reply1");
            service.sendPrompt(SCOPE, "cmd2");
            service.sendReply(SCOPE, "reply2");

            List<PromptService.Message> history = service.getMessageHistory(SCOPE);
            assertEquals(4, history.size());
            assertEquals("cmd1", history.get(0).text());
            assertTrue(history.get(0).fromUser());
            assertEquals("reply1", history.get(1).text());
            assertFalse(history.get(1).fromUser());
            assertEquals("cmd2", history.get(2).text());
            assertEquals("reply2", history.get(3).text());
        }

        @Test
        void ids_are_monotonically_increasing() {
            service.sendPrompt(SCOPE, "cmd");
            service.sendReply(SCOPE, "reply1");
            service.sendReply(SCOPE, "reply2");

            List<PromptService.Message> history = service.getMessageHistory(SCOPE);
            for (int i = 1; i < history.size(); i++) {
                assertTrue(history.get(i).id() > history.get(i - 1).id(),
                        "Message IDs should be monotonically increasing");
            }
        }
    }

    @Nested
    class UpdateLastReply {

        @Test
        void updates_text_of_last_system_message_in_history() {
            service.sendPrompt(SCOPE, "cmd");
            service.sendReply(SCOPE, "original");

            service.updateLastReply(SCOPE, "updated");

            List<PromptService.Message> history = service.getMessageHistory(SCOPE);
            assertEquals(2, history.size());
            assertEquals("updated", history.get(1).text());
        }

        @Test
        void preserves_id_of_updated_message_in_history() {
            service.sendReply(SCOPE, "original");
            long originalId = service.getMessageHistory(SCOPE).get(0).id();

            service.updateLastReply(SCOPE, "updated");

            assertEquals(originalId, service.getMessageHistory(SCOPE).get(0).id());
        }
    }

    @Nested
    class Subscriptions {

        @Test
        void subscriber_receives_replies_in_order() {
            List<PromptService.Message> received = new ArrayList<>();
            service.subscribe(SCOPE, received::add);

            service.sendReply(SCOPE, "first");
            service.sendReply(SCOPE, "second");
            service.sendReply(SCOPE, "third");

            assertEquals(3, received.size());
            assertEquals("first", received.get(0).text());
            assertEquals("second", received.get(1).text());
            assertEquals("third", received.get(2).text());
        }

        @Test
        void unsubscribe_stops_delivery() {
            List<PromptService.Message> received = new ArrayList<>();
            Runnable unsubscribe = service.subscribe(SCOPE, received::add);

            service.sendReply(SCOPE, "before");
            unsubscribe.run();
            service.sendReply(SCOPE, "after");

            assertEquals(1, received.size());
            assertEquals("before", received.get(0).text());
        }

        @Test
        void scopes_are_isolated() {
            List<PromptService.Message> scopeA = new ArrayList<>();
            List<PromptService.Message> scopeB = new ArrayList<>();
            service.subscribe("A", scopeA::add);
            service.subscribe("B", scopeB::add);

            service.sendReply("A", "for-A");
            service.sendReply("B", "for-B");

            assertEquals(1, scopeA.size());
            assertEquals("for-A", scopeA.get(0).text());
            assertEquals(1, scopeB.size());
            assertEquals("for-B", scopeB.get(0).text());
        }
    }

    @Nested
    class HistorySnapshot {

        @Test
        void getMessageHistory_returns_immutable_snapshot() {
            service.sendReply(SCOPE, "msg1");
            List<PromptService.Message> snapshot = service.getMessageHistory(SCOPE);

            service.sendReply(SCOPE, "msg2");

            assertEquals(1, snapshot.size(), "Snapshot should not reflect later additions");
            assertEquals(2, service.getMessageHistory(SCOPE).size());
        }
    }
}
