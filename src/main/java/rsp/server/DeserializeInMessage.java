package rsp.server;


import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import rsp.dom.VirtualDomPath;
import rsp.util.data.Either;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonSimple;

import java.util.Objects;

/**
 * The communication protocol is based on the protocol of the Korolev project by Aleksey Fomkin.
 */
public final class DeserializeInMessage {
    private static final System.Logger logger = System.getLogger(DeserializeInMessage.class.getName());

    private final InMessages inMessages;

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

    public DeserializeInMessage(InMessages inMessages) {
        this.inMessages = inMessages;
    }

    public void parse(String message) {
        Objects.requireNonNull(message);
        final JSONParser jsonParser = new JSONParser();
        try {
            final JSONArray messageJson = (JSONArray) jsonParser.parse(message);
            final int messageType = Math.toIntExact((long)messageJson.get(0));
            switch(messageType) {
                case DOM_EVENT: parseDomEvent((String) messageJson.get(1), messageJson.get(2)); break;
                case EXTRACT_PROPERTY_RESPONSE: parseExtractPropertyResponse((String) messageJson.get(1), messageJson.get(2)); break;
                case EVAL_JS_RESPONSE: parseEvalJsResponse((String) messageJson.get(1),
                                                            messageJson.size() > 2 ? messageJson.get(2) : ""); break;
                case HEARTBEAT: heartBeat(); break;
            }
        } catch (Throwable ex) {
            logger.log(System.Logger.Level.ERROR, "Incoming message parse exception", ex);
        }
    }

    private void parseExtractPropertyResponse(String metadata, Object value) {
        final String[] tokens = metadata.split(":");
        final int descriptorId = Integer.parseInt(tokens[0]);
        final int jsonMetadata = Integer.parseInt(tokens[1]);
        final Either<Throwable, JsonDataType> result = jsonMetadata == JSON_METADATA_DATA ?
                Either.right(JsonSimple.convertToJsonType(value))
                :
                Either.left(new RuntimeException("Property not found"));
        inMessages.handleExtractPropertyResponse(descriptorId,
                                                 result);
    }

    private void parseEvalJsResponse(String metadata, Object value) {
        final String[] tokens = metadata.split(":");
        inMessages.handleEvalJsResponse(Integer.parseInt(tokens[0]),
                                        JsonSimple.convertToJsonType(value));
    }

    private void parseDomEvent(String str, Object eventObject) {
        final JsonDataType json = JsonSimple.convertToJsonType(eventObject);
        if (json instanceof JsonDataType.Object) {
            final String[] tokens = str.split(":");
            inMessages.handleDomEvent(Integer.parseInt(tokens[0]),
                                      VirtualDomPath.of(tokens[1]),
                                      tokens[2],
                                      (JsonDataType.Object) JsonSimple.convertToJsonType(eventObject));
        } else {
            throw new IllegalStateException("Unexpected type of an event object: " + eventObject.getClass().getName());
        }
    }

    private void heartBeat() {
        // no-op
    }
}
