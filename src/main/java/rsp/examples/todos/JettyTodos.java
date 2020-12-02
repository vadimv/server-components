package rsp.examples.todos;

import rsp.App;
import rsp.Component;
import rsp.dsl.RefDefinition;
import rsp.jetty.JettyServer;
import rsp.util.StreamUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static rsp.dsl.Html.*;

public class JettyTodos {

    public static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        final RefDefinition textInputRef = createRef();
        final Component<State> render = useState ->
                html(
                      body(
                           div(text("TODO tracker"),
                           div(style("height", "250px"),
                               style("overflow", "scroll"),
                               of(StreamUtils.zipWithIndex(Arrays.stream(useState.get().todos)).map(todo ->
                                       div(input(attr("type", "checkbox"),
                                                 when(todo.getValue().done, () -> attr("checked", "checked")),
                                                 attr("autocomplete", "off"), /* reset the checkbox on Firefox reload current page */
                                                 on("click", c -> {
                                                   useState.accept(useState.get().toggleDone(todo.getKey()));
                                                 })),
                                           span(when(todo.getValue().done, () -> style("text-decoration", "line-through")),
                                                text(todo.getValue().text))
                                          )))),
                           form(input(textInputRef,
                                      attr("type", "text"),
                                      attr("placeholder", "What should be done?")),
                                button(text("Add todo")),
                                       on("submit", c -> {
                                            var inputProps = c.props(textInputRef);
                                            inputProps.getString("value").thenApply(v -> useState.get().addTodo(v))
                                                                   .thenAccept(s -> { inputProps.set("value", "");
                                                                                      useState.accept(s); });
                                })))));

        final int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        final var s = new JettyServer(port,
                              "",
                                      new App(initialState(),
                                              render));
        s.start();
        s.join();
    }

    static State initialState() {
        final List<Todo> todos = IntStream.range(1, 10).mapToObj(i -> new Todo("This is TODO #" + i, false)).collect(Collectors.toList());
        return new State(todos.toArray(new Todo[0]), Optional.empty());
    }

    static class State {
        public final Todo[] todos;
        public final Optional<Integer> edit;
        public State(Todo[] todos, Optional<Integer> edit) {
            this.todos = todos;
            this.edit = edit;
        }

        public State toggleDone(int todoIndex) {
            final Todo[] newTodos = new Todo[todos.length];
            for (int i=0;i<todos.length;i++) {
                newTodos[i] = i == todoIndex ? new Todo(todos[i].text, !todos[i].done) : todos[i];
            }
            return new State(newTodos, this.edit);
        }

        public State addTodo(String text) {
            final Todo[] newTodos = Arrays.copyOf(todos, todos.length + 1);
            newTodos[todos.length] = new Todo(text, false);
            return new State(newTodos, Optional.empty());
        }
    }

    static class Todo {
        public final String text;
        public final boolean done;
        public Todo(String text, boolean done) {
            this.text = text;
            this.done = done;
        }
    }
}
