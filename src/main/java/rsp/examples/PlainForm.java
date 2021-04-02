package rsp.examples;

import rsp.App;
import rsp.Rendering;
import rsp.dsl.Html;
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

    private static Rendering<String> pages() {
        return s -> of(() -> html(
                headP(title("Page: " + s)),
                body(
                    Html.component(formComponent(), s, str -> str)
                )
            )
        );
    }

/*    private static Component<String> page(String name) {
        return  formComponent().render(s);
    }*/

    private static Rendering<String> formComponent() {
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

    private static Rendering<String> formResult() {
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