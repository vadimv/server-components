package rsp.server;

import rsp.Op;
import rsp.dom.Path;

import java.util.Objects;
import java.util.Optional;

import static rsp.server.ParseInMessage.CallbackType.*;

public class ParseInMessage {
    interface CallbackType {
        int DomEvent = 0; // `$renderNum:$elementId:$eventType`
        int CustomCallback = 1; // `$name:arg`
        int ExtractPropertyResponse = 2; // `$descriptor:$value`
        int History = 3; // URL
        int EvalJsResponse = 4; // `$descriptor:$status:$value`
        int ExtractEventDataResponse = 5; // `$descriptor:$dataJson`
        int Heartbeat = 6; // `$descriptor:$anyvalue`
    }

    public static Optional<InMessage> parse(String message) {
        Objects.requireNonNull(message);
        final String[] tokens = message.substring(1, message.length() - 1).trim().split(",", 2);
        final int messageType = Integer.parseInt(tokens[0]);
        switch(messageType) {
            case DomEvent: return Optional.of(parseDomEvent(tokens[1]));
            case ExtractPropertyResponse: return Optional.of(parseExtractPropertyResponse(tokens[1]));
            case Heartbeat: return Optional.of(InMessage.HEART_BEAT);
        }
        return Optional.empty();
    }

    private static InMessage parseExtractPropertyResponse(String str) {
        final String[] tokens = unquote(str).split(":");
        return new InMessage.ExtractPropertyResponseInMessage(Integer.parseInt(tokens[0]),
                                                              tokens[1]);
    }

    private static InMessage parseDomEvent(String str) {
        final String[] tokens = unquote(str).split(":");
        return new InMessage.DomEventInMessage(Integer.parseInt(tokens[0]),
                                               Path.of(tokens[1]),
                                               tokens[2]);
    }

    private static String unquote(String str) {
        final String trimmed = str.trim();
        return trimmed.substring(1, trimmed.length() - 1);
    }

}
