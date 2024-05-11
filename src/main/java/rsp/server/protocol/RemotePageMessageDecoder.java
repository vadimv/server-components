package rsp.server.protocol;


import rsp.dom.TreePositionPath;
import rsp.server.ExtractPropertyResponse;
import rsp.server.RemoteIn;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonParser;

import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;

/**
 * The implementation of the text-based protocol is based on the protocol of the Korolev project by Aleksey Fomkin.
 */
public final class RemotePageMessageDecoder implements MessageDecoder {
    private static final System.Logger logger = System.getLogger(RemotePageMessageDecoder.class.getName());

    private JsonParser jsonParser;
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

    public RemotePageMessageDecoder(final JsonParser jsonParser, final RemoteIn remoteIn) {
        this.jsonParser = Objects.requireNonNull(jsonParser);
        this.remoteIn = Objects.requireNonNull(remoteIn);
    }

    @Override
    public void decode(final String message) {
        Objects.requireNonNull(message);
        try {
            final JsonDataType.Array messageJson = (JsonDataType.Array) jsonParser.parse(message);
            final int messageType = Math.toIntExact(messageJson.get(0).asJsonNumber().asLong());
            switch (messageType) {
                case DOM_EVENT -> parseDomEvent(messageJson.get(1).toString(),
                                                messageJson.get(2).asJsonObject());
                case EXTRACT_PROPERTY_RESPONSE -> parseExtractPropertyResponse(messageJson.get(1).toString(),
                                                                               messageJson.get(2));
                case EVAL_JS_RESPONSE -> parseEvalJsResponse(messageJson.get(1).toString(),
                                                             messageJson.size() > 2 ? messageJson.get(2) : JsonDataType.String.EMPTY);
                case HEARTBEAT -> heartBeat();
            }
        } catch (final Exception ex) {
            logger.log(System.Logger.Level.ERROR, "Incoming message parse exception", ex);
        }
    }

    private void parseExtractPropertyResponse(final String metadata, final JsonDataType value) {
        final String[] tokens = metadata.split(":");
        final int descriptorId = Integer.parseInt(tokens[0]);
        final int jsonMetadata = Integer.parseInt(tokens[1]);
        final ExtractPropertyResponse result = jsonMetadata == JSON_METADATA_DATA ?
                                        new ExtractPropertyResponse.Value(value) :
                                        ExtractPropertyResponse.NOT_FOUND;
        remoteIn.handleExtractPropertyResponse(descriptorId, result);
    }

    private void parseEvalJsResponse(final String metadata, final JsonDataType value) {
        final String[] tokens = metadata.split(":");
        remoteIn.handleEvalJsResponse(Integer.parseInt(tokens[0]), value);
    }

    private void parseDomEvent(final String str, final JsonDataType.Object eventObject) {
        final String[] tokens = str.split(":");
        remoteIn.handleDomEvent(Integer.parseInt(tokens[0]),
                                TreePositionPath.of(tokens[1]),
                                tokens[2],
                                eventObject);
    }

    private void heartBeat() {
        logger.log(TRACE, () -> "Heartbeat message received");
    }
}
