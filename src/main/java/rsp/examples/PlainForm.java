package rsp.examples;

import rsp.App;
import rsp.ComponentStateFunction;
import rsp.jetty.JettyServer;
import rsp.routing.Route;
import rsp.routing.RoutingDsl;
import rsp.server.HttpRequest;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static rsp.html.HtmlDsl.*;
import static rsp.routing.RoutingDsl.*;

/**
 * An example with plain detached pages:
 * <ul>
 *     <li>a page with an input form</li>
 *     <li>a page with representation of the entered data</li>
 * </ul>
 */
public class PlainForm {
    public static void main(String[] args) {
        final App<Optional<FullName>> app = new App<>(route(),
                                                      pages());
        final var server = new JettyServer<>(8080, "", app);
        server.start();
        server.join();
    }

    public static class FullName {
        public final String firstName;
        public final String secondName;

        public FullName(String firstName, String secondName) {
            this.firstName = Objects.requireNonNull(firstName);
            this.secondName = Objects.requireNonNull(secondName);
        }

        public String toString() {
            return firstName + " " + secondName;
        }
    }

    private static Route<HttpRequest, Optional<FullName>> route() {
        return RoutingDsl.concat(
            get("/*", req -> CompletableFuture.completedFuture(Optional.empty())),
            post("/*",
                  req -> CompletableFuture.completedFuture(Optional.of(new FullName(req.queryParam("firstname").orElseThrow(),
                                                                                    req.queryParam("lastname").orElseThrow())))));
    }

    private static ComponentStateFunction<Optional<FullName>> pages() {
        return (sv, sc) -> html(
                        headPlain(title("Plain Form Pages")),
                        body(
                            sv.isEmpty() ? formComponent().apply(sv) : formResult().apply(sv)
                        )
        );
    }

    private static ComponentStateFunction<Optional<FullName>> formComponent() {
        return (sv, sc) -> div(
                h2(text("HTML Form")),
                form(attr("action", "page0"), attr("method", "post"),
                label(attr("for", "firstname"), text("First name:")),
                input(attr("type", "text"), attr("name","firstname"), attr("value", "First")),
                br(),
                label(attr("for", "lastname"), text("Last name:")),
                input(attr("type", "text"), attr("name","lastname"), attr("value", "Last")),
                br(),
                input(attr("type", "submit"), attr("value", "Submit"))),
                p("If you click the 'Submit' button, the form-data will be sent to page0."));
    }

    private static ComponentStateFunction<Optional<FullName>> formResult() {
        return (sv, sc) -> div(h2(text("HTML Form result")),
                        div(p("The submitted name is " + sv.orElseThrow())));
    }
}