package rsp.server.protocol;


import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import rsp.dom.VirtualDomPath;
import rsp.server.RemoteIn;
import rsp.util.data.Either;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonSimpleUtils;

import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;

/**
 * The implementation of the text-based protocol is based on the protocol of the Korolev project by Aleksey Fomkin.
 */
public final class RemotePageMessageDecoder implements MessageDecoder {
    private static final System.Logger logger = System.getLogger(RemotePageMessageDecoder.class.getName());

    private final RemoteIn remoteIn;

    private static final int DOM_EVENT = 0; // `$renderNum:$elementId:$eventType`
    private static final int CUSTOM_CALLBACK = 1; // `$name:arg`
    private static final int EXTRACT_PROPERTY_RESPONSE = 2; // `$descriptor:$value`
    private static final int HISTORY = 3; // URL
    private static final int EVAL_JS_RESPONSE = 4; // `$descriptor:$status:$value`
    private static final int EXTRACT_EVENT_DATA_RESPONSE = 5; // `$descriptor:$dataJson`
    private static final int HEARTBEAT = 6;

    private static final int JSON_METADATA_DATA = 0;
    private static final int JSON_METADATA_UNDEFINED = 1;
    private static final int JSON_METADATA_FUNCTION = 2;
    private static final int JSON_METADATA_ERROR = 3;

    public RemotePageMessageDecoder(final RemoteIn remoteIn) {
        this.remoteIn = remoteIn;
    }

    @Override
    public void decode(final String message) {
        Objects.requireNonNull(message);
        final JSONParser jsonParser = new JSONParser();
        try {
            final JSONArray messageJson = (JSONArray) jsonParser.parse(message);
            final int messageType = Math.toIntExact((long)messageJson.get(0));
            switch (messageType) {
                case DOM_EVENT -> parseDomEvent((String) messageJson.get(1), messageJson.get(2));
                case EXTRACT_PROPERTY_RESPONSE -> parseExtractPropertyResponse((String) messageJson.get(1), messageJson.get(2));
                case EVAL_JS_RESPONSE -> parseEvalJsResponse((String) messageJson.get(1), messageJson.size() > 2 ? messageJson.get(2) : "");
                case HEARTBEAT -> heartBeat();
            }
        } catch (final Exception ex) {
            logger.log(System.Logger.Level.ERROR, "Incoming message parse exception", ex);
        }
    }

    private void parseExtractPropertyResponse(final String metadata, final Object value) {
        final String[] tokens = metadata.split(":");
        final int descriptorId = Integer.parseInt(tokens[0]);
        final int jsonMetadata = Integer.parseInt(tokens[1]);
        final Either<Throwable, JsonDataType> result = jsonMetadata == JSON_METADATA_DATA ?
                                        Either.right(JsonSimpleUtils.convertToJsonType(value)) :
                                        Either.left(new RuntimeException("Property not found"));
        remoteIn.handleExtractPropertyResponse(descriptorId, result);
    }

    private void parseEvalJsResponse(final String metadata, final Object value) {
        final String[] tokens = metadata.split(":");
        remoteIn.handleEvalJsResponse(Integer.parseInt(tokens[0]), JsonSimpleUtils.convertToJsonType(value));
    }

    private void parseDomEvent(final String str, final Object eventObject) {
        final JsonDataType json = JsonSimpleUtils.convertToJsonType(eventObject);
        if (json instanceof JsonDataType.Object) {
            final String[] tokens = str.split(":");
            remoteIn.handleDomEvent(Integer.parseInt(tokens[0]),
                                    VirtualDomPath.of(tokens[1]),
                                    tokens[2],
                                    (JsonDataType.Object) JsonSimpleUtils.convertToJsonType(eventObject));
        } else {
            throw new IllegalStateException("Unexpected type of an event object: " + eventObject.getClass().getName());
        }
    }

    private void heartBeat() {
        logger.log(TRACE, () -> "Heartbeat message received");
    }
}
