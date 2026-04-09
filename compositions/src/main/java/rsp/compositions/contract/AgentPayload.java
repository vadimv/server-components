package rsp.compositions.contract;

import rsp.util.json.JsonDataType;

import java.util.Objects;

/**
 * Typed wrapper for raw agent payloads.
 * <p>
 * Wraps a {@link JsonDataType} value representing the payload produced by an agent service
 * (LLM response, regex extraction, etc.) before it is parsed into a domain-typed event payload
 * by {@link PayloadParsers}.
 * <p>
 * Use {@link #EMPTY} for actions with no payload (VoidKey events).
 *
 * @param value the JSON-typed payload value
 */
public record AgentPayload(JsonDataType value) {

    /** Payload for actions with no data (VoidKey events, null LLM payloads). */
    public static final AgentPayload EMPTY = new AgentPayload(JsonDataType.Null.INSTANCE);

    public AgentPayload {
        Objects.requireNonNull(value, "value must not be null; use AgentPayload.EMPTY for no-payload actions");
    }

    /** Convenience factory for string payloads. */
    public static AgentPayload of(String s) {
        return new AgentPayload(new JsonDataType.String(s));
    }

    /** Convenience factory for integer payloads. */
    public static AgentPayload of(int n) {
        return new AgentPayload(JsonDataType.Number.of(n));
    }

    /** Convenience factory for long payloads. */
    public static AgentPayload of(long n) {
        return new AgentPayload(JsonDataType.Number.of(n));
    }

    /** Convenience factory for double payloads. */
    public static AgentPayload of(double n) {
        return new AgentPayload(JsonDataType.Number.of(n));
    }

    /** Convenience factory for boolean payloads. */
    public static AgentPayload of(boolean b) {
        return new AgentPayload(new JsonDataType.Boolean(b));
    }

    /**
     * Creates an AgentPayload from a {@link JsonDataType}, substituting {@link #EMPTY} for null.
     */
    public static AgentPayload ofNullable(JsonDataType value) {
        return value == null || value instanceof JsonDataType.Null ? EMPTY : new AgentPayload(value);
    }

    /** Returns true if this is an empty/null payload. */
    public boolean isEmpty() {
        return value instanceof JsonDataType.Null;
    }
}
