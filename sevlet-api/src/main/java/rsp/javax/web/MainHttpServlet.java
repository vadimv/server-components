package rsp.javax.web;

import rsp.server.http.HttpRequest;
import rsp.server.http.HttpResponse;
import rsp.page.HttpHandler;
import rsp.util.ExceptionsUtils;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

import static java.lang.System.Logger.Level.*;

/**
 * An HTTP Servlet API wrapper over the provided HTTP handler.
 */
public final class MainHttpServlet  extends HttpServlet {
    private static final System.Logger logger = System.getLogger(MainHttpServlet.class.getName());

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    private final HttpHandler httpHandler;

    public MainHttpServlet(final HttpHandler httpHandler) {
        this.httpHandler = httpHandler;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
        handleRequestAsync(request, response);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) {
        handleRequestAsync(request, response);
    }

    private void handleRequestAsync(final HttpServletRequest request, final HttpServletResponse response) {
        final AsyncContext asyncContext = request.startAsync();
        asyncContext.start(() -> {
            final HttpRequest req = HttpRequestUtils.httpRequest(request);
            logger.log(TRACE, () -> request.getRemoteAddr() + " -> " + request.getMethod() + " " + request.getRequestURL());
            httpHandler.handle(req).handle((resp, ex) -> {
                if (ex != null) {
                    logger.log(ERROR, "Http rendering exception", ex);
                    return new HttpResponse(500,
                                             Collections.emptyList(),
                                             exceptionDetails(ex));
                } else {
                    return resp;
                }

            }).thenAccept(resp -> {
                setServletResponse(resp, response);
                logger.log(TRACE, () -> request.getRemoteAddr() + " <- " + response.getStatus());
                asyncContext.complete();
            });

        });
    }

    private static String exceptionDetails(final Throwable ex) {
        return "500 Internal server error\n" +
               "Exception: " + ex.getMessage() + "\n" +
               ExceptionsUtils.stackTraceToString(ex);
    }

    private void setServletResponse(final HttpResponse sourceResponse, final HttpServletResponse destinationResponse) {
        destinationResponse.setStatus(sourceResponse.status);

        sourceResponse.headers.stream().forEach(h -> destinationResponse.addHeader(h.name(), h.value()));

        try(final var inputStream = sourceResponse.bodyStream; final var outputStream = destinationResponse.getOutputStream()) {
            copy(inputStream, outputStream);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copy(final InputStream source, final OutputStream target) throws IOException {
        final byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int length;
        while ((length = source.read(buf)) > 0) {
            target.write(buf, 0, length);
        }
    }
}
