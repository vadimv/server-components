package rsp.component;

import rsp.server.Path;
import rsp.server.http.HttpRequest;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Stateful and stateless components definitions domain-specific language helpers.
 */
public class ComponentDsl {

    private ComponentDsl() {}

    /**
     * Creates a stateful component with an initial state provided.
     * @param initialState the component's initial state
     * @param componentView a function for forming the component's view according to a state value
     * @return a component's definition for the DSL
     */
    public static <S> StatefulComponentDefinition<S> component(final S initialState,
                                                               final ComponentView<S> componentView) {
        return new InitialStateComponentDefinition<>("initial-state", initialState, componentView);
    }


    public static <S> StatefulComponentDefinition<S> pathComponent(final Function<Path, CompletableFuture<? extends S>> initialStateRouting,
                                                                   final BiFunction<S, Path, Path> stateToPath,
                                                                   final ComponentView<S> componentView) {
        return new PathStateComponentDefinition<>(initialStateRouting, stateToPath, componentView);
    }

    public static <S> StatefulComponentDefinition<S> webComponent(final Function<HttpRequest, CompletableFuture<? extends S>> initialStateRouting,
                                                                  final ComponentView<S> componentView) {
        return new HttpRequestStateComponentDefinition<>(initialStateRouting,  componentView);
    }
}
