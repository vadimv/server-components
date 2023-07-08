package rsp.server;

import org.junit.Assert;
import org.junit.Test;
import rsp.dom.VirtualDomPath;
import rsp.server.protocol.RemotePageMessageDecoder;
import rsp.util.data.Either;
import rsp.util.json.JsonDataType;

public class RemotePageMessageDecoderTests {

    @Test
    public void should_deserialize_dom_event_correctly() {
        final TestRemoteIn collector = new TestRemoteIn();
        final RemotePageMessageDecoder p = createParser(collector);
        p.decode("[0,\"0:1_2_1_2_2_1:click\",{}]");

        Assert.assertTrue(collector.result instanceof DomEvent);
        final DomEvent result = (DomEvent) collector.result;
        Assert.assertEquals("click", result.eventType);
        Assert.assertEquals(VirtualDomPath.of("1_2_1_2_2_1"), result.path);
        Assert.assertEquals(new JsonDataType.Object(),  result.eventObject);
    }

    @Test
    public void should_deserialize_extract_property() {
        final TestRemoteIn collector = new TestRemoteIn();
        final RemotePageMessageDecoder p = createParser(collector);
        p.decode("[2,\"1:0\",\"bar\"]");

        Assert.assertTrue(collector.result instanceof ExtractProperty);
        final ExtractProperty result = (ExtractProperty) collector.result;
        Assert.assertEquals(1, result.descriptorId);
        result.value.on(v -> Assert.fail(),
                        v -> {
            Assert.assertEquals(new JsonDataType.String("bar"), v);
        });

    }

    @Test
    public void should_deserialize_eval_js_response() {
        final TestRemoteIn collector = new TestRemoteIn();
        final RemotePageMessageDecoder p = createParser(collector);
        p.decode("[4,\"1:0\",\"foo\"]");

        Assert.assertTrue(collector.result instanceof JsResponse);
        final JsResponse result = (JsResponse) collector.result;
        Assert.assertEquals(1, result.descriptorId);
        Assert.assertEquals(new JsonDataType.String("foo"), result.value);
    }

    private RemotePageMessageDecoder createParser(final RemoteIn collector) {
        return new RemotePageMessageDecoder(collector);
    }

    private final class DomEvent {
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

    private final class ExtractProperty {
        public final int descriptorId;
        public final Either<Throwable, JsonDataType> value;
        public ExtractProperty(final int descriptorId, final Either<Throwable, JsonDataType> value) {
            this.descriptorId = descriptorId;
            this.value = value;
        }
    }

    private final class JsResponse {
        public final int descriptorId;
        public final JsonDataType value;
        public JsResponse(final int descriptorId, final JsonDataType value) {
            this.descriptorId = descriptorId;
            this.value = value;
        }
    }

    private final class TestRemoteIn implements RemoteIn {
        public Object result;



        @Override
        public void handleExtractPropertyResponse(final int descriptorId, final Either<Throwable, JsonDataType> value) {
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
