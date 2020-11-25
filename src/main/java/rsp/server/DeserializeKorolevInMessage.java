package rsp.server;


import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import rsp.dom.Path;
import rsp.util.Log;

import java.util.Objects;
import java.util.Optional;

public class DeserializeKorolevInMessage {
    private final InMessages inMessages;
    private final Log.Reporting log;

    private static final int DOM_EVENT = 0; // `$renderNum:$elementId:$eventType`
    private static final int CUSTOM_CALLBACK = 1; // `$name:arg`
    private static final int EXTRACT_PROPERTY_RESPONSE = 2; // `$descriptor:$value`
    private static final int HISTORY = 3; // URL
    private static final int EVAL_JS_RESPONSE = 4; // `$descriptor:$status:$value`
    private static final int EXTRACT_EVENT_DATA_RESPONSE = 5; // `$descriptor:$dataJson`
    private static final int HEARTBEAT = 6;

    public DeserializeKorolevInMessage(InMessages inMessages, Log.Reporting log) {
        this.inMessages = inMessages;
        this.log = log;
    }

    public void parse(String message) {
        Objects.requireNonNull(message);
        final JSONParser jsonParser = new JSONParser();
        try {
            final JSONArray messageJson = (JSONArray) jsonParser.parse(message);
            final int messageType = Math.toIntExact((long)messageJson.get(0));
            switch(messageType) {
                case DOM_EVENT: parseDomEvent((String) messageJson.get(1), (JSONObject) messageJson.get(2)); break;
                case EXTRACT_PROPERTY_RESPONSE: parseExtractPropertyResponse((String) messageJson.get(1)); break;
                case EVAL_JS_RESPONSE: parseEvalJsResponse((String) messageJson.get(1)); break;
                case HEARTBEAT: heartBeat(); break;
            }
        } catch (Throwable ex) {
            log.error(l -> l.log("Incoming message parse exception", ex));
        }
    }

    private void parseExtractPropertyResponse(String str) {
        final String[] tokens = str.split(":");
        inMessages.extractProperty(Integer.parseInt(tokens[0]), tokens.length > 2 ? tokens[2] : "");
    }

    private void parseEvalJsResponse(String str) {
        final String[] tokens = str.split(":");
        inMessages.evalJsResponse(Integer.parseInt(tokens[0]), tokens.length > 2 ? tokens[2] : "");
    }



    private void parseDomEvent(String str, JSONObject eventObject) {
        final String[] tokens = str.split(":");
        inMessages.domEvent(Integer.parseInt(tokens[0]),
                            Path.of(tokens[1]),
                            tokens[2],
                            name -> Optional.ofNullable((String)eventObject.get(name)));
    }

    private void heartBeat() {
        // no-op
    }

    private static String unquote(String str) {
        final String trimmed = str.trim();
        return trimmed.substring(1, trimmed.length() - 1);
    }
}
