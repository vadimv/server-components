package rsp.app.todos;


import rsp.component.ComponentView;
import rsp.component.definitions.InitialStateComponent;
import rsp.jetty.WebServer;
import rsp.ref.ElementRef;
import rsp.util.json.JsonDataType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static rsp.dsl.Html.*;

public class JettyTodos {

    public static void main(String[] args) {
        final ElementRef textInputRef = createElementRef();
        final ComponentView<State> view = newState -> state ->
                html(
                        body(
                                div(text("TODO tracker"),
                                        div(attr("style", "height:250px"),
                                                attr("style", "overflow:scroll"),
                                                of(StreamUtils.zipWithIndex(Arrays.stream(state.todos)).map(todo ->
                                                        div(input(attr("type", "checkbox"),
                                                                        when(todo.getValue().done, () -> attr("checked", "checked")),
                                                                        attr("autocomplete", "off"), /* reset the checkbox on Firefox reload current page */
                                                                        on("click", c -> {
                                                                            newState.setState(state.toggleDone(todo.getKey()));
                                                                        })),
                                                                span(when(todo.getValue().done, () -> attr("style", "text-decoration:line-through")),
                                                                        text(todo.getValue().text))
                                                        )))),
                                        form(input(elementId(textInputRef),
                                                        attr("type", "text"),
                                                        attr("placeholder", "What should be done?")),
                                                button(text("Add todo")),
                                                on("submit",  true,c -> {
                                                    final var textInputProps = c.propertiesByRef(textInputRef);
                                                    textInputProps.get("value").thenApply(v -> {
                                                                if (v instanceof JsonDataType.String todo) {
                                                                    return state.addTodo(todo.value());
                                                                } else {
                                                                    throw new IllegalStateException();
                                                                }

                                                            })
                                                            .thenAccept(s -> {
                                                                textInputProps.set("value", "");
                                                                newState.setState(s);
                                                            });
                                                })))));

        final var server = new WebServer(8080, _ -> new InitialStateComponent<>(initialState(), view));
        server.start();
        server.join();
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
            for (int i=0;i < todos.length;i++) {
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