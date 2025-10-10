package rsp.page;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rsp.server.protocol.RemotePageMessageEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


class TemporaryBufferedRemoteCommandsTests {

    @Test
    void buffers_messages_and_after_switch_sends_all_to_out() {
        final TemporaryBufferedPageCommands remoteOutWithBuffer = new TemporaryBufferedPageCommands();
        final TestMessages testConsumer = new TestMessages();
        //remoteOutWithBuffer.evalJs(1, "1");
        Assertions.assertEquals(0, testConsumer.messages.size());

        //remoteOutWithBuffer.redirectMessagesOut(new RemotePageMessageEncoder(testConsumer));
        //remoteOutWithBuffer.evalJs(2, "2");
        Assertions.assertEquals(2, testConsumer.messages.size());
    }

    private static class TestMessages implements Consumer<String> {
        public List<String> messages = new ArrayList<>();

        @Override
        public void accept(String message) {
            messages.add(message);
        }
    }
}
