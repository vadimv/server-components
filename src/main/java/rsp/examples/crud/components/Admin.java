package rsp.examples.crud.components;

import rsp.App;
import rsp.AppConfig;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.Principal;
import rsp.server.Path;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.Arrays;
import java.util.Optional;
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
                         request -> dispatch(request.path),
                         this::stateToPath,
                         this::appRoot);
    }

    private CompletableFuture<State> dispatch(Path path) {
        final Path.Matcher<State> m = path.matcher(CompletableFuture.completedFuture(error()));
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

    private DocumentPartDefinition appRoot(UseState<Admin.State> us) {
        return html(window().on("popstate",
                                ctx -> ctx.eventObject().apply("path").ifPresent(path -> dispatch(Path.of(path))
                                                                         .thenAccept(s -> us.accept(s)))),
                    body(of(() -> us.get().user.map(user -> div(new MenuPanel().render(useState(() ->
                                    new MenuPanel.State(Arrays.stream(resources).map(r -> new Tuple2<>(r.name, r.title)).collect(Collectors.toList())))),

                            of(Arrays.stream(resources).filter(resource ->
                                    resource.name.equals(us.get().entityName)).map(resource ->
                                    resource.render(useState(() -> us.get().currentResource,
                                            v -> us.accept(us.get().withResource(v))))))))

                            .orElse(div(new LoginForm().render(useState(() -> new LoginForm.State(),
                                                                         lfs -> { if (verifyCredentials(lfs)) us.accept(us.get().withPrincipal(new Principal("usr1")));
                                                                                     else us.accept(us.get().withoutPrincipal()); })))))));
    }

    private boolean verifyCredentials(LoginForm.State lfs) {
        return "admin".equals(lfs.login) & "admin".equals(lfs.password);
    }

    public static class State {
        public final Optional<Principal> user;
        public final String entityName;
        public final Resource.State<?> currentResource;

        public State(Optional<Principal> user, String entityName, Resource.State<?> currentResource) {
            this.user = user;
            this.entityName = entityName;
            this.currentResource = currentResource;
        }

        public State(String entityName, Resource.State<?> currentResource) {
            this.user = Optional.empty();
            this.entityName = entityName;
            this.currentResource = currentResource;
        }

        public State withResource(Resource.State<?> currentResource) {
            return new State(this.user, this.entityName, currentResource);
        }

        public State withPrincipal(Principal user) {
            return new State(Optional.of(user), this.entityName, this.currentResource);
        }

        public State withoutPrincipal() {
            return new State(Optional.empty(), this.entityName, this.currentResource);
        }
    }
}
