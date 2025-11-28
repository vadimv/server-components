package rsp.server;

import org.junit.jupiter.api.Test;
import rsp.dom.TreePositionPath;
import rsp.page.events.*;
import rsp.server.protocol.MessageDecoder;
import rsp.server.protocol.RemotePageMessageDecoder;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonUtils;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class RemotePageMessageDecoderTests {

    @Test
    void should_deserialize_dom_event_correctly() {
        final TestSessonEventsConsumer collector = new TestSessonEventsConsumer();
        final MessageDecoder decoder = createDecoder(collector);

        decoder.decode("[0,\"0:1_2_1_2_2_1:click\",{}]");

        assertTrue(collector.list.get(0) instanceof DomEventNotification);
        final DomEventNotification result = (DomEventNotification) collector.list.getFirst();
        assertEquals("click", result.eventType());
        assertEquals(TreePositionPath.of("1_2_1_2_2_1"), result.path());
        assertEquals(new JsonDataType.Object(),  result.eventObject());
    }


    @Test
    void should_deserialize_extract_property() {
        final TestSessonEventsConsumer collector = new TestSessonEventsConsumer();
        final RemotePageMessageDecoder p = createDecoder(collector);
        p.decode("[2,\"1:0\",\"bar\"]");

        assertTrue(collector.list.getFirst() instanceof ExtractPropertyResponseEvent);
        final ExtractPropertyResponseEvent result = (ExtractPropertyResponseEvent) collector.list.getFirst();
        assertEquals(1, result.descriptorId());
        if (result.result() instanceof ExtractPropertyResponse.NotFound) {
            fail();
        } else if (result.result() instanceof ExtractPropertyResponse.Value v) {
            assertEquals(new JsonDataType.String("bar"), v.value());
        }
    }

    @Test
    void should_deserialize_eval_js_response() {
        final TestSessonEventsConsumer collector = new TestSessonEventsConsumer();
        final RemotePageMessageDecoder p = createDecoder(collector);
        p.decode("[4,\"1:0\",\"foo\"]");

        assertTrue(collector.list.getFirst() instanceof EvalJsResponseEvent);
        final EvalJsResponseEvent result = (EvalJsResponseEvent) collector.list.getFirst() ;
        assertEquals(1, result.descriptorId());
        assertEquals(new JsonDataType.String("foo"), result.value());
    }


    private static RemotePageMessageDecoder createDecoder(final Consumer<SessionEvent> collector) {
        return new RemotePageMessageDecoder(JsonUtils.createParser(), collector);
    }

}
