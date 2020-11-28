package rsp.examples.crud.components;

import rsp.App;
import rsp.Component;
import rsp.dsl.Html;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.util.Tuple2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Admin {
    private final String title;
    private final Resource<?>[] resources;

    public Admin(String title, Resource<?>... resources) {
        this.title = title;
        this.resources = resources;
    }

    public App<State> app() {
        return new App<State>(request -> {
            for (Resource<?> resource : resources) {
                if (request.path.contains(resource.name)) {
                    return resource.entityService.getList(0,10)
                            .thenApply(entities -> new DataGrid.Table<>(entities.toArray(new KeyedEntity[0]),
                                                                        new HashSet<>()))
                            .thenApply(gridState -> new State(resource.name, new Resource.State<>(Set.of(Resource.ViewType.LIST),
                                                                                               gridState,
                                                                                               new DetailsViewState<>())));
                }
            }
            return CompletableFuture.completedFuture(new State("",
                                                               new Resource.State<>(Set.of(Resource.ViewType.ERROR),
                                                                                  DataGrid.Table.empty(),
                                                                                  new DetailsViewState<>())));
        }, appRoot());
    }


    private Component<State> appRoot() {
        return s -> html(
                body(
                        new MenuPanel().render(useState(() ->
                                new MenuPanel.State(Arrays.stream(resources).map(r -> new Tuple2<>(r.name, r.title)).collect(Collectors.toList())))),

                        Html.of(Arrays.stream(resources).filter(resource ->
                                resource.name.equals(s.get().entityName)).map(resource ->
                                    resource.render(useState(() -> s.get().currentResource,
                                                              v -> s.accept(new State(s.get().entityName, v)))))
                        )));
    }

    public static class State {
        public final String entityName;
        public final Resource.State currentResource;

        public State(String entityName, Resource.State currentResource) {
            this.entityName = entityName;
            this.currentResource = currentResource;
        }
    }
}
