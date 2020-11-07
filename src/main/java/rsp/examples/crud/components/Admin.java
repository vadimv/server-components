package rsp.examples.crud.components;

import rsp.App;
import rsp.Component;
import rsp.dsl.Html;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Admin {
    private Resource<?, ?>[] resources;
    public Admin(Resource... resources) {
        this.resources = resources;
    }

    public App<State> app() {
        return new App<State>(request -> {
            for (Resource<?, ?> resource : resources) {
                if (request.path.contains(resource.name)) {
                    return resource.entityService.getList(0,10)
                            .thenApply(entities ->
                                new Grid.GridState(entities.stream().map(b -> b.toRow()).toArray(Grid.Row[]::new),
                                                    0,
                                                    new HashSet<>())).
                            thenApply(gridState -> new State(resource.name, Views.LIST, gridState));
                }
            }
            return CompletableFuture.completedFuture(new State("null", Views.ERROR, null)); //TODO
        }, component());
    }

    private Component<State> component() {
        return s -> html(
                body(window().on("popstate", ctx -> {
                            s.accept(s.get());
                        }),
                        new MenuPanel().render(useState(() ->
                                new MenuPanel.MenuPanelState(Arrays.stream(resources).map(r ->
                                    r.name).collect(Collectors.toList())))),

                        Html.of(Arrays.stream(resources).filter(resource ->
                                resource.name.equals(s.get().entityName)).map(resource -> resource.render(s))
                        )));
    }

    public enum Views {
        LIST, EDIT, CREATE, ERROR
    }

    public static class State {
        public final String entityName;
        public final Views view;
        public final Grid.GridState list;

        public State(String entityName,
                     Views view,
                     Grid.GridState list) {
            this.entityName = entityName;
            this.view = view;
            this.list = list;
        }

        public State updateGridState(Grid.GridState gs) {
            return new State(entityName, view, gs);
        }

        public State updateViewName(Views v) {
            return new State(entityName, v, list);
        }
    }
}
