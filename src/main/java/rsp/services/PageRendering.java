package rsp.services;

import rsp.dom.DomTreeRenderContext;
import rsp.server.HttpRequest;
import rsp.server.HttpResponse;
import rsp.state.MutableState;
import rsp.state.ReadOnly;
import rsp.util.RandomString;
import rsp.util.Tuple2;
import rsp.*;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PageRendering<S> {
    public static final int KEY_LENGTH = 64;
    public static final String DEVICE_ID_COOKIE_NAME = "deviceId";

    private final RandomString randomStringGenerator = new RandomString(KEY_LENGTH);

    private final Component<S> documentDefinition;
    private final Function<HttpRequest, S> useStateFunction;
    private final BiFunction<String, S, String> state2route;

    public final Map<QualifiedSessionId, Page<S>> pagesStorage;

    public PageRendering(Function<HttpRequest, S> useStateFunction,
                         BiFunction<String, S, String> state2route,
                         Component<S> documentDefinition,
                         Map<QualifiedSessionId, Page<S>> pagesStorage) {
        this.useStateFunction = useStateFunction;
        this.state2route = state2route;
        this.documentDefinition = documentDefinition;
        this.pagesStorage = pagesStorage;
    }

    public HttpResponse httpGet(HttpRequest request) {
        final String deviceId = request.getCookie.apply(DEVICE_ID_COOKIE_NAME).orElse(randomStringGenerator.newString());
        final String sessionId = randomStringGenerator.newString();
        final QualifiedSessionId pageId = new QualifiedSessionId(deviceId, sessionId);
        final S initialState = useStateFunction.apply(request);


        final XhtmlRenderContext<S> newCtx = new XhtmlRenderContext<>(TextPrettyPrinting.NO_PRETTY_PRINTING, "<!DOCTYPE html>");
        final EnrichingXhtmlContext<S> enrichingContext = new EnrichingXhtmlContext<>(newCtx,
                                                                                        sessionId,
                                                                                        "/",
                                                                                        DefaultConnectionLostWidget.HTML,
                                                                                        5000);
        final DomTreeRenderContext<S> domTreeContext = new DomTreeRenderContext<>();
        documentDefinition.materialize(new ReadOnly<>(initialState)).accept(new DelegatingRenderContext(enrichingContext, domTreeContext));


        pagesStorage.put(pageId, new Page<S>(request.path,
                                             documentDefinition,
                                             initialState,
                                             state2route,
                                             domTreeContext.root,
                                             domTreeContext.events));

        final List<Tuple2<String,String>> headers = List.of(new Tuple2<>("Content-Type", "text/html"),
                                                            HttpResponse.Headers.setCookie(DEVICE_ID_COOKIE_NAME,
                                                                                           deviceId,
                                                                                           "/",
                                                                                         60 * 60 * 24 * 365 * 10 /* 10 years */ ));
        return  new HttpResponse(200,
                                headers,
                                newCtx.toString());
    }

    public RenderContext<S> renderComponents(S state, String sessionId) {
        final XhtmlRenderContext<S> newCtx = new XhtmlRenderContext<>(TextPrettyPrinting.NO_PRETTY_PRINTING, "<!DOCTYPE html>");
        final EnrichingXhtmlContext<S> enrichingContext = new EnrichingXhtmlContext<>(newCtx,
                                                                sessionId,
                                                           "/",
                                                                DefaultConnectionLostWidget.HTML,
                                                   5000);
        documentDefinition.materialize(new MutableState<>(state)).accept(enrichingContext);
        return enrichingContext;
    }

    private static String stateSessionKey(String deviceId, String sessionId) {
        return deviceId + "-" + sessionId;
    }

}
