package rsp.app.counters;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.dsl.Definition;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpStatus;
import rsp.http.PageResult;
import rsp.http.PageResult;

import static rsp.dsl.Html.*;
import static rsp.dsl.Html.attr;
import static rsp.dsl.Html.body;
import static rsp.dsl.Html.link;

/**
 * The root component of CountersApp.
 */
public class CountersAppComponent extends Component<CountersAppComponent.AppState, Object> {

    private static final Definition NOT_FOUND_PAGE =
            html(head(title("Not found")), body(h1("Not found 404")));
    private final HttpRequest httpRequest;

    public CountersAppComponent(final HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    public static PageResult initialPage(HttpRequest request) {
        return isValid(request)
                ? PageResult.live(new CountersAppComponent(request))
                : PageResult.staticHtml(new CountersAppComponent(request)).status(HttpStatus.NOT_FOUND);
    }

    @Override
    public ComponentStateSupplier<AppState> initStateSupplier() {
        return (_, _) ->
                // the URL path is expected to contain two integers for the counters c1 and c2
                isValid(httpRequest) ?
                new CountersAppState() : new NotFoundState();
    }

    private static boolean isValid(HttpRequest request) {
        return HttpMethod.GET.equals(request.method()) && request.path().matches("^/\\d+/\\d+");
    }

    @Override
    public ComponentView<AppState, Object> componentView() {
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
