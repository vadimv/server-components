package rsp.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Handles serving static resources from a file system directory.
 */
public final class StaticResourceHandler {
    private static final Pattern RESOURCE_PATH_VALIDATION_REGEX = Pattern.compile("^/.*"); // TODO
    private final File baseDirectory;
    private final rsp.url.Path webContextPath;

    /**
     * Creates a new static resource handler.
     * @param baseDirectory the root directory from which to serve files, must not be null
     * @param contextPath a web context to resolve requests to the static resources, must not be null
     */
    public StaticResourceHandler(final File baseDirectory, final String contextPath) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory);
        if (!this.baseDirectory.isDirectory()) {
            throw new IllegalArgumentException("Base path must be a directory: " + this.baseDirectory.getAbsolutePath());
        }


        if (!RESOURCE_PATH_VALIDATION_REGEX.matcher(contextPath).matches()) {
            throw new IllegalArgumentException("Unexpected context path, should be like '/res/': " + contextPath);
        }
        this.webContextPath = Objects.requireNonNull(rsp.url.Path.of(contextPath));
    }

    /**
     * Handles a request for a static resource.
     * @param requestPath the path of the requested resource, must start with the web context path
     * @return an HttpResponse with the file content, or a 404 response if not found or not allowed
     */
    public HttpResponse handle(final rsp.url.Path requestPath) {
        Objects.requireNonNull(requestPath);

        final rsp.url.Path subRequestPath = requestPath.relativize(webContextPath);
        final Path normalizedBase = baseDirectory.toPath().toAbsolutePath().normalize();
        Path targetPath = normalizedBase;
        for (String element : subRequestPath.elements()) {
            // A percent-encoded slash is data in the URL segment, not a filesystem separator.
            if (element.indexOf('/') >= 0 || element.indexOf('\\') >= 0) {
                return HttpResponses.status(404);
            }
            targetPath = targetPath.resolve(element);
        }
        targetPath = targetPath.normalize();

        if (!targetPath.startsWith(normalizedBase)) {
            return HttpResponses.status(403);
        }

        final File file = targetPath.toFile();

        if (!file.exists() || !file.isFile()) {
            return HttpResponses.status(404);
        }

        final MediaType mediaType = MediaType.forFileName(file.getName());
        return HttpResponse.ok()
                .stream(() -> {
                    try {
                        return new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                        throw new UncheckedIOException(e);
                    }
                }, java.util.OptionalLong.of(file.length()), mediaType)
                .build();
    }
}
