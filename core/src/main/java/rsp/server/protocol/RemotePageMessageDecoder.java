package rsp.server.protocol;


import rsp.dom.TreePositionPath;
import rsp.page.events.DomEventNotification;
import rsp.page.events.EvalJsResponseEvent;
import rsp.page.events.ExtractPropertyResponseEvent;
import rsp.page.events.Command;
import rsp.server.ExtractPropertyResponse;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonParser;

import java.util.Objects;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.TRACE;

/**
 * The implementation of the text-based protocol is based on the protocol of the Korolev project by Aleksey Fomkin.
 */
public final class RemotePageMessageDecoder implements MessageDecoder {
    private static final System.Logger logger = System.getLogger(RemotePageMessageDecoder.class.getName());

    private JsonParser jsonParser;
    private final Consumer<Command> remoteIn;

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

    public RemotePageMessageDecoder(final JsonParser jsonParser, final Consumer<Command> remoteIn) {
        this.jsonParser = Objects.requireNonNull(jsonParser);
        this.remoteIn = Objects.requireNonNull(remoteIn);
    }

    @Override
    public void decode(final String message) {
        Objects.requireNonNull(message);
        try {
            if (jsonParser.parse(message) instanceof JsonDataType.Array(JsonDataType[] messageJson) && messageJson.length >= 1) {
                final JsonDataType messageTypeJson = messageJson[0];
                if (messageTypeJson instanceof JsonDataType.Number messageTypeJsonNumber) {
                    final int messageType = Math.toIntExact(messageTypeJsonNumber.asLong());
                    switch (messageType) {
                        case DOM_EVENT -> {
                            if (messageJson.length == 3
                                && messageJson[1] instanceof JsonDataType.String(String str)
                                && messageJson[2] instanceof JsonDataType.Object eventObject) {
                                parseDomEvent(str, eventObject);
                            } else {
                                throw new JsonDataType.JsonException();
                            }
                        }
                        case EXTRACT_PROPERTY_RESPONSE -> {
                            if (messageJson.length == 3 && messageJson[1] instanceof JsonDataType.String(String str)) {
                                parseExtractPropertyResponse(str, messageJson[2]);
                            } else {
                                throw new JsonDataType.JsonException();
                            }
                        }
                        case EVAL_JS_RESPONSE -> {
                            if (messageJson[1] instanceof JsonDataType.String(String metadata)) {
                                if (messageJson.length == 2) {
                                    parseEvalJsResponse(metadata, JsonDataType.String.EMPTY);
                                } else if (messageJson.length == 3) {
                                    parseEvalJsResponse(metadata, messageJson[2]);
                                } else {
                                    throw new JsonDataType.JsonException();
                                }
                            }
                        }
                        case HEARTBEAT -> heartBeat();
                    }
                }
            }

        } catch (final Exception ex) {
            logger.log(System.Logger.Level.ERROR, "Incoming message parse exception", ex);
        }
    }

    private void parseExtractPropertyResponse(final String metadata, final JsonDataType value) {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(value);
        final String[] tokens = metadata.split(":");
        final int descriptorId = Integer.parseInt(tokens[0]);
        final int jsonMetadata = Integer.parseInt(tokens[1]);
        final ExtractPropertyResponse result = jsonMetadata == JSON_METADATA_DATA ?
                                        new ExtractPropertyResponse.Value(value) :
                                        ExtractPropertyResponse.NOT_FOUND;
        remoteIn.accept(new ExtractPropertyResponseEvent(descriptorId, result));
    }

    private void parseEvalJsResponse(final String metadata, final JsonDataType value) {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(value);
        final String[] tokens = metadata.split(":");
        remoteIn.accept(new EvalJsResponseEvent(Integer.parseInt(tokens[0]), value));
    }



    private void parseDomEvent(final String str, final JsonDataType.Object eventObject) {
        Objects.requireNonNull(str);
        Objects.requireNonNull(eventObject);
        final String[] tokens = str.split(":");
        remoteIn.accept(new DomEventNotification(Integer.parseInt(tokens[0]),
                                     TreePositionPath.of(tokens[1]),
                                     tokens[2],
                                     eventObject));
    }

    private void heartBeat() {
        logger.log(TRACE, () -> "Heartbeat message received");
    }
}
