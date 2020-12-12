package rsp.examples.crud.components;

import rsp.App;
import rsp.AppConfig;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.server.HttpRequest;
import rsp.server.Path;
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
    private final Resource[] resources;

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
        final Path.Matcher<State> m = request.path.matcher(CompletableFuture.completedFuture(error()));
        for (Resource<?> resource : resources) {
            final Path.Matcher<State> sm = m.when((name) -> name.equals(resource.name),
                                                  (name) -> resource.initialListState().thenApply(resourceState -> new State(resource.name, resourceState)))
                                            .when((name, key) -> name.equals(resource.name),
                                                  (name, key) -> resource.initialListStateWithEdit(key).thenApply(resourceState -> new State(resource.name, resourceState)));
            if (sm.isMatch) {
                    return sm.state;
            }
        }
            return m.state;
    }

    private State error() {
        return new State("",
                new Resource.State<>(Set.of(Resource.ViewType.ERROR),
                        DataGrid.Table.empty(),
                        new DetailsViewState<>()));
    }

    private Path stateToPath(Path oldPath, Admin.State s) {
        return new Path(s.entityName, s.currentResource.edit.currentKey.orElse(""));
    }

    private DocumentPartDefinition appRoot(UseState<Admin.State> s) {
        return html(
                    body(
                            new MenuPanel().render(useState(() ->
                                    new MenuPanel.State(Arrays.stream(resources).map(r -> new Tuple2<>(r.name, r.title)).collect(Collectors.toList())))),

                            of(Arrays.stream(resources).filter(resource ->
                                    resource.name.equals(s.get().entityName)).map(resource ->
                                        resource.render(useState(() -> s.get().currentResource,
                                                                  v -> s.accept(new State(s.get().entityName, v)))))
                    )));
    }

    public static class State {
        public final String entityName;
        public final Resource.State<?> currentResource;

        public State(String entityName, Resource.State<?> currentResource) {
            this.entityName = entityName;
            this.currentResource = currentResource;
        }
    }
}
