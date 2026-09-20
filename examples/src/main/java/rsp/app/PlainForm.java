package rsp.app;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.component.View;
import rsp.dsl.Html;
import rsp.dsl.Tag;
import rsp.http.PageResult;
import rsp.http.HttpRouter;
import rsp.http.Router;
import rsp.http.WebServer;
import rsp.http.PageResult;
import rsp.http.HttpResponse;

import java.net.URI;
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
    private static final String FORM_PATH = "/forms/name";

    static void main(final String[] args) {
        final Router routes = HttpRouter.builder()
                .get(FORM_PATH, (_, _) -> page(new EmptyName()))
                .post(FORM_PATH, (request, _) -> page(new FullName(
                        Objects.requireNonNull(request.query().parameterValue("firstname")),
                        Objects.requireNonNull(request.query().parameterValue("lastname")))))
                .get("/", (_, _) -> PageResult.redirect(URI.create(FORM_PATH)))
                .get("/api/health", (_, _) -> HttpResponse.ok().text("ok").build())
                .build();
        final var server = new WebServer(8080).routes(routes);
        server.start();
        server.join();
    }

    private static PageResult page(Name initialState) {
        return PageResult.staticHtml(new Component<Name, Object>() {
            @Override
            public ComponentStateSupplier<Name> initStateSupplier() {
                return (_, _) -> initialState;
            }

            @Override
            public ComponentView<Name, Object> componentView() {
                return _ -> pagesView();
            }
        });
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
                        head(title("Plain Form Pages")),
                        body(
                            state instanceof FullName ? formResult((FullName)state) : form()
                        )
        );
    }

    private static Tag form() {
        return div(
                h2(text("HTML Form")),
                Html.form(attr("action", FORM_PATH), attr("method", "post"),
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
