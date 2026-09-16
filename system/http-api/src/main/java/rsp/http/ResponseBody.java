package rsp.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;

/** A response entity that can open its stream when the transport writes it. */
public interface ResponseBody {
    ResponseBody EMPTY = bytes(new byte[0]);

    InputStream openStream();

    OptionalLong contentLength();

    static ResponseBody text(String value) {
        return bytes(Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8));
    }

    static ResponseBody bytes(byte[] value) {
        byte[] copy = Objects.requireNonNull(value, "value").clone();
        return stream(() -> new ByteArrayInputStream(copy), OptionalLong.of(copy.length));
    }

    static ResponseBody stream(Supplier<? extends InputStream> streams, OptionalLong contentLength) {
        Objects.requireNonNull(streams, "streams");
        Objects.requireNonNull(contentLength, "contentLength");
        if (contentLength.isPresent() && contentLength.getAsLong() < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        return new ResponseBody() {
            @Override
            public InputStream openStream() {
                return Objects.requireNonNull(streams.get(), "response stream");
            }

            @Override
            public OptionalLong contentLength() {
                return contentLength;
            }
        };
    }
}
