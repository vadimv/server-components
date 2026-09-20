package rsp.http;

import rsp.component.definitions.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Initial-page outcome: render a component with response metadata, or redirect. */
public sealed interface PageResult extends HttpResult permits PageResult.Render, PageResult.Redirect {
    static Render live(Component<?, ?> component) {
        return new Render(component, true, HttpStatus.OK, List.of(), List.of());
    }

    static Render staticHtml(Component<?, ?> component) {
        return new Render(component, false, HttpStatus.OK, List.of(), List.of());
    }

    static Redirect redirect(String location) {
        return redirect(URI.create(location));
    }

    static Redirect redirect(URI location) {
        return new Redirect(HttpStatus.FOUND, location, HttpHeaders.EMPTY);
    }

    record Render(Component<?, ?> component,
                  boolean live,
                  HttpStatus status,
                  List<HttpHeader> headers,
                  List<SetCookie> cookies) implements PageResult {
        public Render {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(status, "status");
            headers = List.copyOf(Objects.requireNonNull(headers, "headers"));
            cookies = List.copyOf(Objects.requireNonNull(cookies, "cookies"));
        }

        public Render status(HttpStatus value) {
            return new Render(component, live, value, headers, cookies);
        }

        public Render header(String name, String value) {
            List<HttpHeader> next = new ArrayList<>(headers);
            next.add(new HttpHeader(name, value));
            return new Render(component, live, status, next, cookies);
        }

        public Render cookie(SetCookie value) {
            List<SetCookie> next = new ArrayList<>(cookies);
            next.add(Objects.requireNonNull(value, "value"));
            return new Render(component, live, status, headers, next);
        }
    }

    record Redirect(HttpStatus status, URI location, HttpHeaders headers) implements PageResult {
        public Redirect {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(headers, "headers");
            if (status.code() < 300 || status.code() >= 400) {
                throw new IllegalArgumentException("Redirect status must be 3xx: " + status.code());
            }
        }

        public Redirect header(String name, String value) {
            return new Redirect(status, location,
                    HttpHeaders.builder().addAll(headers).add(name, value).build());
        }
    }
}
