package rsp.javax.web;

import rsp.server.HttpRequest;
import rsp.server.HttpResponse;
import rsp.services.PageRendering;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

public class MainHttpServlet<S>  extends HttpServlet {
    private final PageRendering<S> pageRendering;

    public MainHttpServlet(PageRendering<S> app) {
        this.pageRendering = app;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final HttpRequest req = new HttpRequest(request.getPathInfo(),
                                                s -> Optional.ofNullable(request.getParameter(s)),
                                                n -> ServletUtils.cookie(request, n).map(c -> c.getValue()));
        final HttpResponse resp = pageRendering.httpGet(req);
        setServletResponse(resp, response);
    }

    private void setServletResponse(HttpResponse resp, HttpServletResponse response) throws IOException {
        response.setStatus(resp.status);
        resp.headers.stream().forEach(h -> response.addHeader(h._1, h._2));
        response.getWriter().print(resp.body);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
 /*       UseState<S> useState = storage.get(stateSessionKey(request));
        if(useState == null) {
            useState = useStateFunction.apply(request);
        }

        final XhtmlRenderContext<S> currentContext = renderComponents(useState);
        final EventContext<S> eventData = new EventContext<>(request, useState);
        currentContext.events.stream().filter(e -> e.eventType.equals("onSubmit")).forEach(e -> e.eventHandler.accept(eventData));

        storage.put(stateSessionKey(request), useState);

        final XhtmlRenderContext<S> newContext = renderComponents(useState);

        setHeaders(response);
        write(response, newContext.toString());*/
    }


}