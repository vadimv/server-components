package rsp.javax.web;

import rsp.server.http.HttpRequest;
import rsp.server.http.HttpResponse;
import rsp.page.PageRendering;
import rsp.util.ExceptionsUtils;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

import static java.lang.System.Logger.Level.*;

public final class MainHttpServlet<S>  extends HttpServlet {
    private static final System.Logger logger = System.getLogger(MainHttpServlet.class.getName());

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    private final PageRendering<S> pageRendering;

    public MainHttpServlet(final PageRendering<S> pageRendering) {
        this.pageRendering = pageRendering;
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
            pageRendering.httpResponse(req).handle((resp, ex) -> {
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
        final StringBuilder sb = new StringBuilder();
        sb.append("500 Internal server error\n");
        sb.append("Exception: " + ex.getMessage() + "\n");
        sb.append(ExceptionsUtils.stackTraceToString(ex));
        return sb.toString();
    }

    private void setServletResponse(final HttpResponse resp, final HttpServletResponse response) {
        response.setStatus(resp.status);

        resp.headers.stream().forEach(h -> response.addHeader(h._1, h._2));

        try(final var inputStream = resp.bodyStream; final var outputStream = response.getOutputStream()) {
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
