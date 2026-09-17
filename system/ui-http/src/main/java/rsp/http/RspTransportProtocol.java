package rsp.http;

import rsp.util.json.JsonDataType;
import rsp.util.json.JsonUtils;
import rsp.websocket.WebSocketProtocolException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static rsp.websocket.WebSocketCloseCodes.PROTOCOL_ERROR;

/**
 * Transport controls layered around the existing RSP application protocol.
 */
final class RspTransportProtocol {
    static final int VERSION = 2;

    static final int RESUME = 7;
    static final int ACKNOWLEDGE = 8;
    static final int TERMINATE = 9;

    static final int FRAME = 17;
    static final int RESUME_ACCEPTED = 18;
    static final int RESUME_REJECTED = 19;
    static final int FRAME_BATCH = 20;

    private RspTransportProtocol() {
    }

    static String frame(final long sequence, final String applicationMessage) {
        return "[" + FRAME + "," + sequence + "," + applicationMessage + "]";
    }

    static String frameBatch(final long firstSequence, final List<String> applicationMessages) {
        Objects.requireNonNull(applicationMessages);
        if (applicationMessages.isEmpty()) {
            throw new IllegalArgumentException("applicationMessages must not be empty");
        }
        return "[" + FRAME_BATCH + "," + firstSequence + ",["
               + String.join(",", applicationMessages) + "]]";
    }

    static String resumeAccepted(final long currentSequence) {
        return "[" + RESUME_ACCEPTED + "," + currentSequence + "]";
    }

    static String resumeRejected(final String reason) {
        return "[" + RESUME_REJECTED + ",\"" + JsonUtils.escape(reason) + "\"]";
    }

    static Optional<ClientControl> decodeClientControl(final String message) throws WebSocketProtocolException {
        final JsonDataType parsed;
        try {
            parsed = JsonUtils.parse(message);
        } catch (final JsonDataType.JsonException ex) {
            throw protocolError("Invalid RSP message", ex);
        }
        if (!(parsed instanceof JsonDataType.Array(JsonDataType[] elements)) || elements.length == 0) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "RSP message must be a non-empty array");
        }
        if (!(elements[0] instanceof JsonDataType.Number typeNumber) || !typeNumber.isIntegral()) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "RSP message type must be an integer");
        }

        final int type;
        try {
            type = Math.toIntExact(typeNumber.asLong());
        } catch (final ArithmeticException ex) {
            throw protocolError("RSP message type is out of range", ex);
        }
        return switch (type) {
            case RESUME -> Optional.of(decodeResume(elements));
            case ACKNOWLEDGE -> Optional.of(new Acknowledge(nonNegativeLong(elements, 2, "acknowledgement sequence")));
            case TERMINATE -> {
                requireLength(elements, 1, "terminate");
                yield Optional.of(Terminate.INSTANCE);
            }
            default -> Optional.empty();
        };
    }

    private static Resume decodeResume(final JsonDataType[] elements) throws WebSocketProtocolException {
        requireLength(elements, 3, "resume");
        final long version = integralLong(elements[1], "protocol version");
        final long lastAppliedSequence = integralLong(elements[2], "last applied sequence");
        if (version < 0 || version > Integer.MAX_VALUE) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "Invalid protocol version");
        }
        if (lastAppliedSequence < 0) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "Invalid last applied sequence");
        }
        return new Resume((int) version, lastAppliedSequence);
    }

    private static long nonNegativeLong(final JsonDataType[] elements,
                                        final int expectedLength,
                                        final String fieldName) throws WebSocketProtocolException {
        requireLength(elements, expectedLength, fieldName);
        final long value = integralLong(elements[1], fieldName);
        if (value < 0) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "Invalid " + fieldName);
        }
        return value;
    }

    private static long integralLong(final JsonDataType value,
                                     final String fieldName) throws WebSocketProtocolException {
        if (!(value instanceof JsonDataType.Number number) || !number.isIntegral()) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, fieldName + " must be an integer");
        }
        try {
            return number.value().longValueExact();
        } catch (final ArithmeticException ex) {
            throw protocolError(fieldName + " is out of range", ex);
        }
    }

    private static void requireLength(final JsonDataType[] elements,
                                      final int expectedLength,
                                      final String messageName) throws WebSocketProtocolException {
        if (elements.length != expectedLength) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR,
                                                 "Invalid " + messageName + " message length");
        }
    }

    private static WebSocketProtocolException protocolError(final String message, final Exception cause) {
        final WebSocketProtocolException result = new WebSocketProtocolException(PROTOCOL_ERROR, message);
        result.initCause(cause);
        return result;
    }

    sealed interface ClientControl permits Resume, Acknowledge, Terminate {
    }

    record Resume(int protocolVersion, long lastAppliedSequence) implements ClientControl {
    }

    record Acknowledge(long sequence) implements ClientControl {
    }

    enum Terminate implements ClientControl {
        INSTANCE
    }
}
