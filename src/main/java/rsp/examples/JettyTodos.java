package rsp.examples;

import rsp.Component;
import rsp.Page;
import rsp.QualifiedSessionId;
import rsp.javax.web.MainHttpServlet;
import rsp.javax.web.MainWebSocketEndpoint;
import rsp.jetty.JettyServer;
import rsp.server.HttpRequest;
import rsp.services.PageRendering;
import rsp.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static rsp.dsl.Html.*;

public class JettyTodos {

    public static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        final var textInputRef = createRef();
        final Component<State> render = useState ->
                html(
                      body(
                           div(text("TODO tracker"),
                           div(style("height", "250px"),
                               style("overflow", "scroll"),
                               of(CollectionUtils.zipWithIndex(Arrays.stream(useState.get().todos)).map(todo ->
                                       div(input(attr("type", "checkbox")),
                                           span(when(todo.getValue().done, style("text-decoration", "line-through")),
                                                               text(todo.getValue().text)),
                                           event("click", c -> {
                                               useState.accept(useState.get().toggleDone(todo.getKey()));
                                           }))))),
                               form(input(textInputRef,
                                          attr("type", "text"),
                                          attr("placeholder", "What should be done?")),
                                    button(text("Add todo")),
                                    event("submit", c -> {
                                        c.value(textInputRef).thenApply(v -> useState.get().addTodo(v))
                                                             .thenAccept(s -> useState.accept(s));
                                    })))));

        final State initialState = initialState();
        final Function<HttpRequest, State> routes = request -> initialState;
        final BiFunction<String, State, String> state2path = (c, s) -> c;
        final Map<QualifiedSessionId, Page<State>> pagesStorage = new ConcurrentHashMap<>();
        final PageRendering<State> pageRendering = new PageRendering<>(routes, state2path, render, pagesStorage);

        final int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        final var s = new JettyServer(port,
                                "",
                                    new MainHttpServlet<>(pageRendering),
                                    new MainWebSocketEndpoint(pagesStorage));
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
            for(int i=0;i<todos.length;i++) {
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
