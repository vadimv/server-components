package rsp.server.protocol;

public interface MessageDecoder {
    /**
     * Decodes a message.
     * @param message the message to decode, must not be null
     */
    void decode(final String message);
}
