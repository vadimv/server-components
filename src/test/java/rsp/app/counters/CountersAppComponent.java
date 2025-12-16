package rsp.app.counters;

import rsp.component.ComponentView;
import rsp.component.definitions.HttpRequestStateComponent;
import rsp.dsl.Definition;
import rsp.routing.Routing;
import rsp.server.http.HttpRequest;

import java.util.function.Function;

import static rsp.dsl.Html.*;
import static rsp.dsl.Html.attr;
import static rsp.dsl.Html.body;
import static rsp.dsl.Html.link;
import static rsp.routing.RoutingDsl.get;

/**
 * The root component of CountersApp.
 */
public class CountersAppComponent extends HttpRequestStateComponent<CountersAppComponent.AppState> {

    private static final Definition NOT_FOUND_PAGE =
            html(head(HeadType.PLAIN, title("Not found")),
                 body(h1("Not found 404"))).statusCode(404);

    public CountersAppComponent(final HttpRequest httpRequest) {
        super(httpRequest);
    }

    @Override
    public Function<HttpRequest, AppState> routing() {
        return new Routing<>(get("/:c1(^-?\\d+$)/:c2(^-?\\d+$)", _ -> new CountersAppState()),
                             new NotFoundState());
    }

    @Override
    public ComponentView<AppState> componentView() {
        return _ ->state -> switch(state) {
            case CountersAppState _ -> page(httpRequest);
            case NotFoundState _ -> NOT_FOUND_PAGE;
        };
    }

    private static Definition page(final HttpRequest httpRequest) {
        return html(head(title("Counters"),
                                link(attr("rel", "stylesheet"),
                                        attr("href", "/res/style.css"))),
                    body(new CountersMainComponent(httpRequest.relativeUrl())));
    }

    public sealed interface AppState {
    }

    public record NotFoundState() implements AppState {
    }

    public record CountersAppState() implements AppState {
    }
}
