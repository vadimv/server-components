package rsp.examples;

import rsp.App;
import rsp.jetty.JettyServer;

import static rsp.dsl.Html.*;

public class PlainForm {
    public static void main(String[] args) throws Exception {
        final var app = new App<>("Hello world!",
                                  s -> of(() -> html(
                    head(UpgradeMode.RAW, title("Plain form")),
                    body(
                         h2(text("HTML Forms")),
                            form(attr("action", "page0"), attr("method", "post"),
                                 label(attr("for", "firstname"), text("First name:")),
                                 input(attr("type", "text"), attr("name","firstname"), attr("value", "First")),
                                 br(),
                                 label(attr("for", "lastname"), text("Last name:")),
                                 input(attr("type", "text"), attr("name","lastname"), attr("value", "Last")),
                                 br(),
                                 input(attr("type", "submit"), attr("value", "Submit"))
                            ),
                            p("If you click the 'Submit' button, the form-data will be sent to page0.")
                    )
            )
        ));
        final var server = new JettyServer(8080, "", app);
        server.start();
        server.join();
    }
}