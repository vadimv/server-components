package rsp.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** A bounded, immutable request body. */
public final class RequestBody {
    public static final RequestBody EMPTY = new RequestBody(new byte[0]);

    private final byte[] bytes;

    private RequestBody(byte[] bytes) {
        this.bytes = bytes;
    }

    public static RequestBody of(byte[] bytes, int maximumBytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (maximumBytes < 0) throw new IllegalArgumentException("maximumBytes must not be negative");
        if (bytes.length > maximumBytes) {
            throw new BodyLimitExceededException(maximumBytes, bytes.length);
        }
        return bytes.length == 0 ? EMPTY : new RequestBody(bytes.clone());
    }

    public int size() {
        return bytes.length;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public String text() {
        return text(StandardCharsets.UTF_8);
    }

    public String text(Charset charset) {
        return new String(bytes, Objects.requireNonNull(charset, "charset"));
    }

    public InputStream stream() {
        return new ByteArrayInputStream(bytes);
    }

    public static final class BodyLimitExceededException extends IllegalArgumentException {
        public BodyLimitExceededException(int maximumBytes, int actualBytes) {
            super("Request body is " + actualBytes + " bytes; maximum is " + maximumBytes);
        }
    }
}
