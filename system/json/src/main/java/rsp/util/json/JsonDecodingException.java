package rsp.util.json;

/** Indicates that a valid JSON value does not have the shape required by a codec. */
public final class JsonDecodingException extends IllegalArgumentException {
    public JsonDecodingException(String message) {
        super(message);
    }

    public JsonDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
