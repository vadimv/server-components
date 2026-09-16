package rsp.http;

import rsp.component.definitions.Component;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** DSL for selecting live/static UI pages and initial HTTP redirects. */
public final class Pages {
    private Pages() {
    }

    public static PageApplication live(Function<HttpRequest, ? extends Component<?, ?>> pages) {
        Objects.requireNonNull(pages, "pages");
        return request -> live(pages.apply(request));
    }

    public static PageApplication staticHtml(Function<HttpRequest, ? extends Component<?, ?>> pages) {
        Objects.requireNonNull(pages, "pages");
        return request -> staticHtml(pages.apply(request));
    }

    public static PageResult.Render live(Component<?, ?> component) {
        return new PageResult.Render(component, true, HttpStatus.OK, List.of(), List.of());
    }

    public static PageResult.Render staticHtml(Component<?, ?> component) {
        return new PageResult.Render(component, false, HttpStatus.OK, List.of(), List.of());
    }

    public static PageResult.Redirect redirect(String location) {
        return redirect(URI.create(location));
    }

    public static PageResult.Redirect redirect(URI location) {
        return new PageResult.Redirect(HttpStatus.FOUND, location, HttpHeaders.EMPTY);
    }

    public static PageResult.Response response(HttpResponse response) {
        return new PageResult.Response(response);
    }
}
