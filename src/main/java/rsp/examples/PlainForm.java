package rsp.examples;

import rsp.App;
import rsp.Render;
import rsp.dsl.Component;
import rsp.jetty.JettyServer;

import static rsp.dsl.Html.*;

public class PlainForm {
    public static void main(String[] args) {
        final var app = new App<>(new State(new Person("FirstName", "LastName")),
                                  pages());
        final var server = new JettyServer(8080, "", app);
        server.start();
        server.join();
    }

    private static Render<State> pages() {
        return s -> of(() -> html(
                headP(title("Page: " + s.person.firstName)),
                body(
                    new Component.Default<>(formComponent(), State::new).render(s.person)
                )
            )
        );
    }


    private static Render<Person> formComponent() {
        return s -> div(
                h2(text("HTML Form")),
                form(attr("action", "page0"), attr("method", "post"),
                label(attr("for", "firstname"), text("First name:")),
                input(attr("type", "text"), attr("name","firstname"), attr("value", s.firstName)),
                br(),
                label(attr("for", "lastname"), text("Last name:")),
                input(attr("type", "text"), attr("name","lastname"), attr("value", s.lastName)),
                br(),
                input(attr("type", "submit"), attr("value", "Submit"))),
                p("If you click the 'Submit' button, the form-data will be sent to page0."));
    }

    private static Render<String> formResult() {
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

    static class State {
        public final Person person;

        State(Person person) {
            this.person = person;
        }
    }

    static class Person {
        public final String firstName;
        public final String lastName;

        Person(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }
}