package rsp.examples.crud.components;

import rsp.App;
import rsp.Component;
import rsp.dsl.Html;
import rsp.examples.crud.State;

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
                            thenApply(gridState -> new State(resource.name, "list", gridState));
                }
            }
            return CompletableFuture.completedFuture(new State("null", "null", null));
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
}
