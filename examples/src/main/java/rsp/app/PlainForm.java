package rsp.app;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.component.View;
import rsp.dsl.Html;
import rsp.dsl.Tag;
import rsp.jetty.WebServer;

import java.util.Objects;

import static rsp.dsl.Html.*;

/**
 * An example with plain web pages:
 * <ul>
 *     <li>a page with an input form</li>
 *     <li>a page with representation of the entered data</li>
 * </ul>
 */
public class PlainForm {
    static void main(final String[] args) {
        final var server = new WebServer(8080, httpRequest -> new Component<Name>() {

            @Override
            public ComponentStateSupplier<Name> initStateSupplier() {
                return (_, _) ->
                    switch (httpRequest.method) {
                        case GET -> new EmptyName();
                        case POST -> new FullName(Objects.requireNonNull(httpRequest.queryParameters.parameterValue("firstname")),
                                                  Objects.requireNonNull(httpRequest.queryParameters.parameterValue("lastname")));
                        default -> throw new IllegalStateException("Unexpected HTTP mehtod: " + httpRequest.method);
                    };
            }

            @Override
            public ComponentView<Name> componentView() {
                return _ -> pagesView();
            }
        });
        server.start();
        server.join();
    }

    public sealed interface Name {}

    public record FullName(String firstName, String secondName) implements Name {
        public FullName(final String firstName, final String secondName) {
            this.firstName = Objects.requireNonNull(firstName);
            this.secondName = Objects.requireNonNull(secondName);
        }

        @Override
        public String toString() {
            return firstName + " " + secondName;
        }
    }

    public record EmptyName() implements Name {}

    private static View<Name> pagesView() {
        return state -> html(
                        head(HeadType.PLAIN, title("Plain Form Pages")),
                        body(
                            state instanceof FullName ? formResult((FullName)state) : form()
                        )
        );
    }

    private static Tag form() {
        return div(
                h2(text("HTML Form")),
                Html.form(attr("action", "page0"), attr("method", "post"),
                     label(attr("for", "firstname"), text("First name:")),
                     input(attr("type", "text"), attr("name","firstname"), attr("value", "First")),
                     br(),
                     label(attr("for", "lastname"), text("Last name:")),
                     input(attr("type", "text"), attr("name","lastname"), attr("value", "Last")),
                     br(),
                     input(attr("type", "submit"), attr("value", "Submit"))),
                p("If you click the 'Submit' button, the form-data will be sent to page0."));
    }

    private static Tag formResult(FullName state) {
        return div(h2(text("HTML Form result")),
                      div(p("The submitted name is " + state)));
    }
}
