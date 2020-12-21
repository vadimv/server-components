package rsp.examples.crud.components;

import rsp.App;
import rsp.AppConfig;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.Principal;
import rsp.examples.crud.services.Auth;
import rsp.server.Path;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;
import static rsp.state.UseState.useState;

public class Admin {
    private final String title;
    private final Resource[] resources;

    private static final Map<String, Principal> principals = new ConcurrentHashMap<>();

    private final Auth auth = new Auth();

    public Admin(String title, Resource<?>... resources) {
        this.title = title;
        this.resources = resources;
    }

    public App<State> app() {
        return new App<>(AppConfig.DEFAULT,
                         request -> dispatch(request.deviceId().flatMap(id -> Optional.ofNullable(principals.get(id))),
                                             request.path),
                         this::stateToPath,
                         this::appRoot);
    }

    private CompletableFuture<State> dispatch(Optional<Principal> principal, Path path) {

        final Path.Matcher<State> m = path.matcher(CompletableFuture.completedFuture(error()))
                                          .when((name) -> "login".equals(name),
                                                (name) -> CompletableFuture.completedFuture(new Admin.State(Optional.empty(), Optional.empty())));
        for (Resource<?> resource : resources) {
            final Path.Matcher<State> sm = m.when((name) -> name.equals(resource.name),
                                                  (name) -> resource.initialListState().thenApply(resourceState -> new State(principal, Optional.of(resourceState))))
                                            .when((name, key) -> name.equals(resource.name),
                                                  (name, key) -> resource.initialListStateWithEdit(key).thenApply(resourceState -> new State(principal, Optional.of(resourceState))));
            if (sm.isMatch) {
                return sm.state;
            }
        }
        return m.state;
    }

    private State error() {
        return new State(Optional.empty(), Optional.empty());
    }

    private Path stateToPath(Admin.State s) {
        if (s.user.isPresent()) {
            if (s.currentResource.isPresent()) {
                if (s.currentResource.get().details.isPresent()) {
                    return Path.of(s.currentResource.get().name + "/" + s.currentResource.get().details.get().currentKey.orElse("create"));
                } else {
                    return Path.of(s.currentResource.get().name);
                }
            } else {
                return Path.EMPTY_ABSOLUTE;
            }
        } else {
            return Path.of("login");
        }
    }

    private DocumentPartDefinition appRoot(UseState<Admin.State> us) {
        return html(window().on("popstate",
                                ctx -> ctx.eventObject().apply("path").ifPresent(path -> dispatch(us.get().user, Path.of(path))
                                                                         .thenAccept(s -> us.accept(s)))),
                    head(title(title + us.get().currentResource.map(r -> ": " + r.title).orElse("")),
                         link(attr("rel", "stylesheet"), attr("href","/res/style.css"))),
                    body(us.get().user.map(u -> div(div(span(u.name),
                                                        a("#", "Logout", on("click", ctx -> {
                                                            principals.remove(ctx.sessionId().deviceId);
                                                            us.accept(us.get().withoutPrincipal());
                                                         }))),
                                                    new MenuPanel().render(useState(() ->
                                        new MenuPanel.State(Arrays.stream(resources).map(r -> new Tuple2<>(r.name, r.title)).collect(Collectors.toList())))),
                                div(of(us.get().currentResource.flatMap(r -> findResourceComponent(r)).map(p -> p._2.render(useState(() -> p._1,
                                                                                                                            v -> us.accept(us.get().withResource(Optional.of(v)))))).stream()))))

                                .orElse(div(new LoginForm().render(useState(() -> new LoginForm.State(),
                                                               lfs -> auth.authenticate(lfs.userName, lfs.password)
                                                                            .thenAccept(po -> po.ifPresentOrElse(p -> lfs.deviceId.ifPresent(id -> {
                                                                                principals.put(id, p);
                                                                                us.accept(us.get().withPrincipal(p));
                                                                            }),
                                                                                    () -> us.accept(us.get().withoutPrincipal())))))))
                    ));
    }

    private Optional<Tuple2<Resource.State, Resource>> findResourceComponent(Resource.State resourceState) {
        return Arrays.stream(resources).filter(resource -> resource.name.equals(resourceState.name)).map(component -> new Tuple2<>(resourceState, component)).findFirst();
    }

    public static class State {
        public final Optional<Principal> user;
        public final Optional<Resource.State<?>> currentResource;

        public State(Optional<Principal> user, Optional<Resource.State<?>> currentResource) {
            this.user = user;
            this.currentResource = currentResource;
        }

        public State withResource(Optional<Resource.State<?>> currentResource) {
            return new State(this.user, currentResource);
        }

        public State withPrincipal(Principal user) {
            return new State(Optional.of(user), this.currentResource);
        }

        public State withoutPrincipal() {
            return new State(Optional.empty(), this.currentResource);
        }
    }
}
