package rsp.http;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** A normalized media type value. */
public record MediaType(String value) {
    public static final MediaType OCTET_STREAM = new MediaType("application/octet-stream");
    public static final MediaType JSON = new MediaType("application/json");
    public static final MediaType HTML_UTF_8 = new MediaType("text/html; charset=utf-8");
    public static final MediaType TEXT_UTF_8 = new MediaType("text/plain; charset=utf-8");

    private static final Map<String, String> FILE_TYPES = Map.ofEntries(
            Map.entry("html", "text/html"), Map.entry("css", "text/css"),
            Map.entry("js", "application/javascript"), Map.entry("json", "application/json"),
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"), Map.entry("ico", "image/x-icon"),
            Map.entry("txt", "text/plain"), Map.entry("xml", "application/xml"),
            Map.entry("pdf", "application/pdf"), Map.entry("zip", "application/zip"));

    public MediaType {
        value = Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[!#$%&'*+.^_`|~0-9a-z-]+/[!#$%&'*+.^_`|~0-9a-z-]+(?:\\s*;.*)?")) {
            throw new IllegalArgumentException("Invalid media type: " + value);
        }
    }

    public static MediaType parse(String value) {
        return new MediaType(value);
    }

    public static MediaType forFileName(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return OCTET_STREAM;
        }
        return new MediaType(FILE_TYPES.getOrDefault(fileName.substring(dot + 1).toLowerCase(Locale.ROOT),
                OCTET_STREAM.value));
    }

    @Override
    public String toString() {
        return value;
    }
}
