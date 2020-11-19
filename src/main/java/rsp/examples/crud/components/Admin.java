package rsp.examples.crud.components;

import rsp.App;
import rsp.Component;
import rsp.dsl.Html;
import rsp.examples.crud.state.Row;
import rsp.examples.crud.state.Table;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Admin {
    private Resource<?>[] resources;
    public Admin(Resource... resources) {
        this.resources = resources;
    }

    public App<State> app() {
        return new App<State>(request -> {
            for (Resource<?> resource : resources) {
                if (request.path.contains(resource.name)) {
                    return resource.entityService.getList(0,10)
                            .thenApply(entities ->
                                new Table<>(entities.stream().map(b -> b.toRow()).toArray(Row[]::new),
                                            new HashSet<>())).
                            thenApply(gridState -> new State(resource.name, Set.of(Views. LIST), gridState, Optional.empty()));
                }
            }
            return CompletableFuture.completedFuture(new State("",
                                                               Set.of(Views.ERROR),
                                                               Table.empty(),
                                                               Optional.empty()));
        }, component());
    }

    private Component<State> component() {
        return s -> html(
                body(
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
        public final Set<Views> view;
        public final Table list;
        public final Optional edit;

        public State(String entityName,
                     Set<Views> view,
                     Table list,
                     Optional edit) {
            this.entityName = entityName;
            this.view = view;
            this.list = list;
            this.edit = edit;
        }

        public State updateGridState(Table<?, ?> gs) {
            return new State(entityName, view, gs, edit);
        }

        public State updateEdit(Optional<Row<?, ?>> e) {
            return new State(entityName, view, list, e);
        }

        public State updateList(Table<?, ?> l) {
            return new State(entityName, view, l, edit);
        }
    }
}
