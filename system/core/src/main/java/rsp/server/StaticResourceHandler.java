package rsp.server;

import rsp.server.http.Header;
import rsp.server.http.HttpResponse;
import rsp.server.http.MimeType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Handles serving static resources from a file system directory.
 */
public final class StaticResourceHandler {
    private static final Pattern RESOURCE_PATH_VALIDATION_REGEX = Pattern.compile("^/.*"); // TODO
    private final File baseDirectory;
    private final rsp.server.Path webContextPath;

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
        this.webContextPath = Objects.requireNonNull(rsp.server.Path.of(contextPath));
    }

    /**
     * Checks if this handler should handle the given request path.
     * @param requestPath the path of the requested resource
     * @return true if this handler should handle the request, false otherwise
     */
    public boolean shouldHandle(final rsp.server.Path requestPath) {
        return requestPath.startsWith(webContextPath);
    }

    /**
     * Handles a request for a static resource.
     * @param requestPath the path of the requested resource, must start with the web context path
     * @return an HttpResponse with the file content, or a 404 response if not found or not allowed
     */
    public HttpResponse handle(final rsp.server.Path requestPath) {
        Objects.requireNonNull(requestPath);
        
        // 1. Resolve the path relative to the base directory and normalize it
        final rsp.server.Path subRequestPath = requestPath.relativize(webContextPath);
        final Path targetPath = baseDirectory.toPath().resolve(subRequestPath.toString()).normalize();

        // 2. Security Check: Ensure the resolved path is still within the base directory to prevent directory traversal
        if (!targetPath.startsWith(baseDirectory.toPath())) {
            return new HttpResponse(403, Collections.emptyList(), "Forbidden");
        }

        final File file = targetPath.toFile();

        // 3. Check if file exists and is a regular file (not a directory)
        if (!file.exists() || !file.isFile()) {
            return new HttpResponse(404, Collections.emptyList(), "Not Found");
        }

        // 4. Serve the file content
        try {
            final String mimeType = MimeType.of(file.getName());
            return new HttpResponse(200,
                                    Collections.singletonList(new Header("Content-Type", mimeType)),
                                    new FileInputStream(file));
        } catch (final FileNotFoundException e) {
            // Handle race condition where file might be deleted between check and open
            return new HttpResponse(404, Collections.emptyList(), "Not Found");
        }
    }
}
