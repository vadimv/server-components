package rsp.compositions.block;

import rsp.util.json.JsonDataType;

import java.util.Objects;

/**
 * Typed wrapper for actions payloads.
 * <p>
 * Wraps a {@link JsonDataType} value representing the payload produced by an agent service
 * (LLM response, regex extraction, etc.) before it is parsed into a domain-typed event payload
 * by {@link PayloadParsers}.
 * <p>
 * Use {@link #EMPTY} for actions with no payload (VoidKey events).
 *
 * @param value the JSON-typed payload value
 */
public record BlockActionPayload(JsonDataType value) {

    /** Payload for actions with no data (VoidKey events, null LLM payloads). */
    public static final BlockActionPayload EMPTY = new BlockActionPayload(JsonDataType.Null.INSTANCE);

    public BlockActionPayload {
        Objects.requireNonNull(value, "value must not be null; use AgentPayload.EMPTY for no-payload actions");
    }

    /** Convenience factory for string payloads. */
    public static BlockActionPayload of(String s) {
        return new BlockActionPayload(new JsonDataType.String(s));
    }

    /** Convenience factory for integer payloads. */
    public static BlockActionPayload of(int n) {
        return new BlockActionPayload(JsonDataType.Number.of(n));
    }

    /** Convenience factory for long payloads. */
    public static BlockActionPayload of(long n) {
        return new BlockActionPayload(JsonDataType.Number.of(n));
    }

    /** Convenience factory for double payloads. */
    public static BlockActionPayload of(double n) {
        return new BlockActionPayload(JsonDataType.Number.of(n));
    }

    /** Convenience factory for boolean payloads. */
    public static BlockActionPayload of(boolean b) {
        return new BlockActionPayload(new JsonDataType.Boolean(b));
    }

    /**
     * Creates an AgentPayload from a {@link JsonDataType}, substituting {@link #EMPTY} for null.
     */
    public static BlockActionPayload ofNullable(JsonDataType value) {
        return value == null || value instanceof JsonDataType.Null ? EMPTY : new BlockActionPayload(value);
    }

    /** Returns true if this is an empty/null payload. */
    public boolean isEmpty() {
        return value instanceof JsonDataType.Null;
    }
}
