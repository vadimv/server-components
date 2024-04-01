package rsp.component;

import rsp.server.Path;
import rsp.server.http.HttpStateOrigin;
import rsp.server.http.RelativeUrl;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Stateful and stateless components definitions domain-specific language functions.
 */
public class ComponentDsl {

    private ComponentDsl() {}

    /**
     * Creates a stateful component with an initial state provided.
     * @param initialState the component's initial state
     * @param componentView a function for forming the component's view according to a state value
     * @return a component's definition for the DSL
     */
    public static <S> PathStatefulComponentDefinition<S> component(final S initialState,
                                                                   final ComponentView<S> componentView) {
        Objects.requireNonNull(initialState);
        return component(__ -> CompletableFuture.completedFuture(initialState),
                         (__, path) -> path,
                         componentView);
    }

    public static <S> PathStatefulComponentDefinition<S> component(final Function<Path, CompletableFuture<? extends S>> initialStateRouting,
                                                                   final BiFunction<S, Path, Path> state2PathFunction,
                                                                   final ComponentView<S> componentView) {
        Objects.requireNonNull(initialStateRouting);
        Objects.requireNonNull(state2PathFunction);
        Objects.requireNonNull(componentView);

        return new PathStatefulComponentDefinition<>("path-component") {

            @Override
            protected Function<HttpStateOrigin, CompletableFuture<? extends S>> resolveStateFunction() {
                return httpStateOrigin -> initialStateRouting.apply(httpStateOrigin.relativeUrl().path());
            }

            @Override
            protected BiFunction<S, Path, Path> state2pathFunction() {
                return state2PathFunction;
            }

            @Override
            protected Function<RelativeUrl, CompletableFuture<? extends S>> relativeUrlToStateFunction() {
                return relativeUrl -> initialStateRouting.apply(relativeUrl.path());
            }

            @Override
            protected ComponentView<S> componentView() {
                return componentView;
            }
        };
    }
}
