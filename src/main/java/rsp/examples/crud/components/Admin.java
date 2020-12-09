package rsp.examples.crud.components;

import rsp.App;
import rsp.AppConfig;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.server.HttpRequest;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;
import static rsp.state.UseState.useState;

public class Admin {
    private final String title;
    private final Resource<?>[] resources;

    public Admin(String title, Resource<?>... resources) {
        this.title = title;
        this.resources = resources;
    }

    public App<State> app() {
        return new App<>(AppConfig.DEFAULT,
                         this::dispatch,
                         this::stateToPath,
                         this::appRoot);
    }

    private CompletableFuture<State> dispatch(HttpRequest request) {
        for (Resource<?> resource : resources) {
            if (request.path.contains(resource.name)) {
                return resource.initialState().thenApply(resourceState -> new State(resource.name, resourceState));
            }
        }
        return CompletableFuture.completedFuture(new State("",
                                                            new Resource.State<>(Set.of(Resource.ViewType.ERROR),
                                                                                 DataGrid.Table.empty(),
                                                                                 new DetailsViewState<>())));
    }

    private String stateToPath(String oldPath, State s) {
        return "/" + s.entityName + "/" + s.currentResource.edit.currentKey.orElse("");
    }

    private DocumentPartDefinition appRoot(UseState<Admin.State> s) {
        return html(
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
