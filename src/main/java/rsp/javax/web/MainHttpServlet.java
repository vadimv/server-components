package rsp.javax.web;

import rsp.server.HttpRequest;
import rsp.server.HttpResponse;
import rsp.services.PageRendering;
import rsp.util.ExceptionsUtils;
import rsp.util.Log;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

public class MainHttpServlet<S>  extends HttpServlet {
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    private final PageRendering<S> pageRendering;
    private final Log.Reporting log;

    public MainHttpServlet(PageRendering<S> pageRendering, Log.Reporting log) {
        this.pageRendering = pageRendering;
        this.log = log;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        final AsyncContext asyncContext = request.startAsync();
        asyncContext.start(() -> {
            final HttpRequest req = HttpRequest.of(request);

            pageRendering.httpGet(req).handle((resp, ex) -> {
                    if (ex != null) {
                        log.error(l -> l.log("Http rendering exception", ex));
                        return new HttpResponse(500,
                                                      Collections.emptyList(),
                                                      exceptionDetails(ex));
                    } else {
                        return resp;
                    }

            }).thenAccept(resp -> {
                setServletResponse(resp, response);
                asyncContext.complete();
            });

        });
    }

    private static String exceptionDetails(Throwable ex) {
        final StringBuilder sb = new StringBuilder();
        sb.append("500 Internal server error\n");
        sb.append("Exception: " + ex.getMessage() + "\n");
        sb.append(ExceptionsUtils.stackTraceToString(ex));
        return sb.toString();
    }

    private void setServletResponse(HttpResponse resp, HttpServletResponse response) {
        response.setStatus(resp.status);

        resp.headers.stream().forEach(h -> response.addHeader(h._1, h._2));

        try(var inputStream = resp.bodyStream; var outputStream = response.getOutputStream()) {
            copy(inputStream, outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copy(InputStream source, OutputStream target) throws IOException {
        final byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int length;
        while ((length = source.read(buf)) > 0) {
            target.write(buf, 0, length);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    }


}
