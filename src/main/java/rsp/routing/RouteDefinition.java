package rsp.routing;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

public class RouteDefinition<T, S> implements Route<T, S>
{
    private final Predicate<T> predicate;
    private final Function<T, CompletableFuture<S>> matchFun;

    public RouteDefinition(Predicate<T> predicate,
                           Function<T, CompletableFuture<S>> matchFun) {
        this.predicate = predicate;
        this.matchFun = matchFun;
    }

    @Override
    public Optional<CompletableFuture<? extends S>> apply(T value) {
        if (predicate.test(value)) {
            return Optional.of(matchFun.apply(value));
        }
        return Optional.empty();
    }
}
