package rsp.server.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A utility for determining the MIME type of a file based on its extension.
 */
public final class MimeType {

    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html");
        MIME_TYPES.put("css", "text/css");
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("json", "application/json");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("txt", "text/plain");
        MIME_TYPES.put("xml", "application/xml");
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("zip", "application/zip");
    }

    private MimeType() {}

    /**
     * Guesses the MIME type from a file name.
     * @param fileName the file name
     * @return the MIME type, or "application/octet-stream" if unknown
     */
    public static String of(final String fileName) {
        Objects.requireNonNull(fileName);
        final int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "application/octet-stream";
        }
        final String extension = fileName.substring(lastDot + 1).toLowerCase();
        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
