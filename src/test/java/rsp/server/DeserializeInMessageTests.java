package rsp.server;

import org.junit.Assert;
import org.junit.Test;
import rsp.dom.VirtualDomPath;
import rsp.util.Log;
import rsp.util.json.JsonDataType;

public class DeserializeInMessageTests {

    @Test
    public void should_deserialize_dom_event_correctly() {
        final TestInMessages collector = new TestInMessages();
        final DeserializeInMessage p = createParser(collector);
        p.parse("[0,\"0:1_2_1_2_2_1:click\",{}]");

        Assert.assertTrue(collector.result instanceof DomEvent);
        final DomEvent result = (DomEvent) collector.result;
        Assert.assertEquals("click", result.eventType);
        Assert.assertEquals(VirtualDomPath.of("1_2_1_2_2_1"), result.path);
    }

    @Test
    public void should_deserialize_extract_property() {
        final TestInMessages collector = new TestInMessages();
        final DeserializeInMessage p = createParser(collector);
        p.parse("[2,\"1:0\",\"bar\"]");

        Assert.assertTrue(collector.result instanceof ExtractProperty);
        final ExtractProperty result = (ExtractProperty) collector.result;
        Assert.assertEquals(1, result.descriptorId);
    }

    @Test
    public void should_deserialize_eval_js_response() {
        final TestInMessages collector = new TestInMessages();
        final DeserializeInMessage p = createParser(collector);
        p.parse("[4,\"1:0\",\"foo\"]");

        Assert.assertTrue(collector.result instanceof JsResponse);
        final JsResponse result = (JsResponse) collector.result;
        Assert.assertEquals(1, result.descriptorId);
    }

    private DeserializeInMessage createParser(InMessages collector) {
        return new DeserializeInMessage(collector, new Log.Default(Log.Level.OFF, new Log.SimpleFormat(), s -> {}));
    }

    private final class DomEvent {
        final int renderNumber;
        final VirtualDomPath path;
        final String eventType;
        final JsonDataType.Object eventObject;

        public DomEvent(int renderNumber, VirtualDomPath path, String eventType, JsonDataType.Object eventObject) {
            this.renderNumber = renderNumber;
            this.path = path;
            this.eventType = eventType;
            this.eventObject = eventObject;
        }
    }

    private final class ExtractProperty {
        public final int descriptorId;
        public final JsonDataType value;
        public ExtractProperty(int descriptorId, JsonDataType value) {
            this.descriptorId = descriptorId;
            this.value = value;
        }
    }

    private final class JsResponse {
        public final int descriptorId;
        public final JsonDataType value;
        public JsResponse(int descriptorId, JsonDataType value) {
            this.descriptorId = descriptorId;
            this.value = value;
        }
    }

    private final class TestInMessages implements InMessages {
        public Object result;

        @Override
        public void handleExtractPropertyResponse(int descriptorId, JsonDataType value) {
            result = new ExtractProperty(descriptorId, value);
        }

        @Override
        public void handleDomEvent(int renderNumber, VirtualDomPath path, String eventType, JsonDataType.Object eventObject) {
            result = new DomEvent(renderNumber, path, eventType, eventObject);
        }

        @Override
        public void handleEvalJsResponse(int descriptorId, JsonDataType value) {
            result = new JsResponse(descriptorId, value);
        }
    }
}
