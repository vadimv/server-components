package rsp.server;

import rsp.dom.Path;

import java.util.Objects;

public class DeserializeKorolevInMessage {
    private final InMessages inMessages;

    private static final int DOM_EVENT = 0; // `$renderNum:$elementId:$eventType`
    private static final int CUSTOM_CALLBACK = 1; // `$name:arg`
    private static final int EXTRACT_PROPERTY_RESPONSE = 2; // `$descriptor:$value`
    private static final int HISTORY = 3; // URL
    private static final int EVAL_JS_RESPONSE = 4; // `$descriptor:$status:$value`
    private static final int EXTRACT_EVENT_DATA_RESPONSE = 5; // `$descriptor:$dataJson`
    private static final int HEARTBEAT = 6;

    public DeserializeKorolevInMessage(InMessages inMessages) {
        this.inMessages = inMessages;
    }

    public void parse(String message) {
        Objects.requireNonNull(message);
        final String[] tokens = message.substring(1, message.length() - 1).trim().split(",", 2);
        final int messageType = Integer.parseInt(tokens[0]);
        switch(messageType) {
            case DOM_EVENT: parseDomEvent(tokens[1]); break;
            case EXTRACT_PROPERTY_RESPONSE: parseExtractPropertyResponse(tokens[1]); break;
            case EVAL_JS_RESPONSE: parseEvalJsResponse(tokens[1]); break;
            case HEARTBEAT: heartBeat(); break;
        }
    }

    private void parseExtractPropertyResponse(String str) {
        final String[] tokens = unquote(str).split(":");
        inMessages.extractProperty(Integer.parseInt(tokens[0]), tokens.length > 2 ? tokens[2] : "");
    }

    private void parseEvalJsResponse(String str) {
        final String[] tokens = unquote(str).split(":");
        inMessages.evalJsResponse(Integer.parseInt(tokens[0]), tokens.length > 2 ? tokens[2] : "");
    }

    private void parseDomEvent(String str) {
        final String[] tokens = unquote(str).split(":");
        inMessages.domEvent(Integer.parseInt(tokens[0]), Path.of(tokens[1]), tokens[2]);
    }

    private void heartBeat() {
        // no-op
    }

    private static String unquote(String str) {
        final String trimmed = str.trim();
        return trimmed.substring(1, trimmed.length() - 1);
    }
}
