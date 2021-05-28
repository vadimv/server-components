package rsp.examples;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.server.HttpRequest;

import java.util.concurrent.CompletableFuture;

import static rsp.dsl.Html.*;

/**
 * The application state type is a String representing a full name.
 * On empty string show the input form, otherwise show the result page.
 */
public class PlainForm {
    public static void main(String[] args) {
        final var app = new App<>(PlainForm::routes,
                                  pages());
        final var server = new JettyServer(8080, "", app);
        server.start();
        server.join();
    }

    private static CompletableFuture<String> routes(HttpRequest r) {
        return r.method.equals(HttpRequest.Methods.POST) ?
                CompletableFuture.completedFuture(r.getParam("firstname").orElseThrow()
                        + " "
                        + r.getParam("lastname").orElseThrow())
                : CompletableFuture.completedFuture("");
    }

    private static Component<String> pages() {
        return s -> html(
                        headPlain(title("Plain Form Pages")),
                        body(
                            "".equals(s.get()) ? formComponent().render(s) : formResult().render(s)
                        )
        );
    }

    private static Component<String> formComponent() {
        return s -> div(
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

    private static Component<String> formResult() {
        return s -> div(
                h2(text("HTML Form result")),
                div(p("The submitted name is " + s.get())));
    }
}