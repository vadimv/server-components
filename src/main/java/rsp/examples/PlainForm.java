package rsp.examples;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;

import static rsp.dsl.Html.*;

public class PlainForm {
    public static void main(String[] args) {
        final var app = new App<>("Hello world!",
                                  pages());
        final var server = new JettyServer(8080, "", app);
        server.start();
        server.join();
    }

    private static Component<String> pages() {
        return s -> of(() -> html(
                headPlain(title("Page: " + s.get())),
                body(
                    formComponent().render(s)
                )
            )
        );
    }

/*    private static Component<String> page(String name) {
        return  formComponent().render(s);
    }*/

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

    private static String onSubmit() {
        return "";
    }
}