package rsp.examples;

import rsp.component.HttpRequestStateComponentDefinition;
import rsp.component.StatefulComponentDefinition;
import rsp.component.View;
import rsp.html.TagDefinition;
import rsp.jetty.WebServer;
import rsp.routing.Routing;
import rsp.routing.Route;
import rsp.routing.RoutingDsl;
import rsp.server.http.HttpRequest;

import java.util.Objects;
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
    public static void main(final String[] args) {
        final Routing<HttpRequest, Name> routing = new Routing<>(route(), new EmptyName());
        final View<Name> pagesView = pagesView();
        final StatefulComponentDefinition<Name> rootComponent = new HttpRequestStateComponentDefinition<>(routing,
                                                                                                          pagesView);
        final var server = new WebServer(8080, rootComponent);
        server.start();
        server.join();
    }

    public sealed interface Name {}

    public record FullName(String firstName, String secondName) implements Name {
        public FullName(final String firstName, final String secondName) {
            this.firstName = Objects.requireNonNull(firstName);
            this.secondName = Objects.requireNonNull(secondName);
        }

        public String toString() {
            return firstName + " " + secondName;
        }
    }

    public record EmptyName() implements Name {}

    private static Route<HttpRequest, Name> route() {
        return RoutingDsl.concat(
            get("/*", req -> CompletableFuture.completedFuture(new EmptyName())),
            post("/*",
                  req -> CompletableFuture.completedFuture(new FullName(req.queryParam("firstname").orElseThrow(),
                                                                        req.queryParam("lastname").orElseThrow()))));
    }

    private static View<Name> pagesView() {
        return state -> html(
                        head(HeadType.PLAIN, title("Plain Form Pages")),
                        body(
                            state instanceof FullName ? formResult((FullName)state) : formComponent()
                        )
        );
    }

    private static TagDefinition formComponent() {
        return div(
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

    private static TagDefinition formResult(FullName state) {
        return div(h2(text("HTML Form result")),
                      div(p("The submitted name is " + state)));
    }
}