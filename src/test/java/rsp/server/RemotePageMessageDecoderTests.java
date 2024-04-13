package rsp.server;



import org.junit.jupiter.api.Test;
import rsp.dom.VirtualDomPath;
import rsp.server.protocol.MessageDecoder;
import rsp.server.protocol.RemotePageMessageDecoder;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonSimpleUtils;

import static org.junit.jupiter.api.Assertions.*;

public class RemotePageMessageDecoderTests {

    @Test
    public void should_deserialize_dom_event_correctly() {
        final TestRemoteIn collector = new TestRemoteIn();
        final MessageDecoder p = createDecoder(collector);
        p.decode("[0,\"0:1_2_1_2_2_1:click\",{}]");

        assertTrue(collector.result instanceof DomEvent);
        final DomEvent result = (DomEvent) collector.result;
        assertEquals("click", result.eventType);
        assertEquals(VirtualDomPath.of("1_2_1_2_2_1"), result.path);
        assertEquals(new JsonDataType.Object(),  result.eventObject);
    }

    @Test
    public void should_deserialize_extract_property() {
        final TestRemoteIn collector = new TestRemoteIn();
        final RemotePageMessageDecoder p = createDecoder(collector);
        p.decode("[2,\"1:0\",\"bar\"]");

        assertTrue(collector.result instanceof ExtractProperty);
        final ExtractProperty result = (ExtractProperty) collector.result;
        assertEquals(1, result.descriptorId);
        if (result.value instanceof ExtractPropertyResponse.NotFound) {
            fail();
        } else if (result.value instanceof ExtractPropertyResponse.Value v) {
            assertEquals(new JsonDataType.String("bar"), v.value());
        }
    }

    @Test
    public void should_deserialize_eval_js_response() {
        final TestRemoteIn collector = new TestRemoteIn();
        final RemotePageMessageDecoder p = createDecoder(collector);
        p.decode("[4,\"1:0\",\"foo\"]");

        assertTrue(collector.result instanceof JsResponse);
        final JsResponse result = (JsResponse) collector.result;
        assertEquals(1, result.descriptorId);
        assertEquals(new JsonDataType.String("foo"), result.value);
    }

    private static RemotePageMessageDecoder createDecoder(final RemoteIn collector) {
        return new RemotePageMessageDecoder(JsonSimpleUtils.createParser(), collector);
    }

    private static final class DomEvent {
        final int renderNumber;
        final VirtualDomPath path;
        final String eventType;
        final JsonDataType.Object eventObject;

        public DomEvent(final int renderNumber, final VirtualDomPath path, final String eventType, final JsonDataType.Object eventObject) {
            this.renderNumber = renderNumber;
            this.path = path;
            this.eventType = eventType;
            this.eventObject = eventObject;
        }
    }

    private static final class ExtractProperty {
        public final int descriptorId;
        public final ExtractPropertyResponse value;
        public ExtractProperty(final int descriptorId, final ExtractPropertyResponse value) {
            this.descriptorId = descriptorId;
            this.value = value;
        }
    }

    private static final class JsResponse {
        public final int descriptorId;
        public final JsonDataType value;
        public JsResponse(final int descriptorId, final JsonDataType value) {
            this.descriptorId = descriptorId;
            this.value = value;
        }
    }

    private static final class TestRemoteIn implements RemoteIn {
        public Object result;



        @Override
        public void handleExtractPropertyResponse(final int descriptorId, final ExtractPropertyResponse value) {
            result = new ExtractProperty(descriptorId, value);
        }

        @Override
        public void handleDomEvent(final int renderNumber, final VirtualDomPath path, final String eventType, final JsonDataType.Object eventObject) {
            result = new DomEvent(renderNumber, path, eventType, eventObject);
        }

        @Override
        public void handleEvalJsResponse(final int descriptorId, final JsonDataType value) {
            result = new JsResponse(descriptorId, value);
        }
    }
}
