package rsp.routing;

import org.junit.Assert;
import org.junit.Test;
import rsp.server.HttpRequest;
import rsp.server.Path;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static rsp.routing.RoutingDsl.*;

public class RoutingTests {

    @Test
    public void should_correctly_route_simple_for_method() throws ExecutionException, InterruptedException {
        final Routes<String> r = concat(
                get("/*", req -> CompletableFuture.completedFuture("A")),
                post("/*", req -> CompletableFuture.completedFuture("B")),
                get("/*", req -> CompletableFuture.completedFuture("C")));
        final URI requestUri = URI.create("http://localhost");
        final var s = r.apply(new HttpRequest(HttpRequest.HttpMethod.POST, requestUri, Path.of(requestUri.getPath())));
        Assert.assertTrue(s.isPresent());
        Assert.assertEquals("B", s.get().get());
    }

    @Test
    public void should_correctly_route_simple_for_path() throws ExecutionException, InterruptedException {
        final Routes<String> r = concat(
                get("/A", req -> CompletableFuture.completedFuture("A")),
                get("/B", req -> CompletableFuture.completedFuture("B")),
                get("/C", req -> CompletableFuture.completedFuture("C")));
        final URI requestUri = URI.create("http://localhost/B");
        final var s = r.apply(new HttpRequest(HttpRequest.HttpMethod.GET, requestUri, Path.of(requestUri.getPath())));
        Assert.assertTrue(s.isPresent());
        Assert.assertEquals("B", s.get().get());
    }

    @Test
    public void should_correctly_route_simple_for_sub_path() throws ExecutionException, InterruptedException {
        final Routes<String> r = concat(get(req -> paths()),
                                        post("/B", req -> CompletableFuture.completedFuture("C")));
        final URI requestUri = URI.create("http://localhost/B");
        final var s = r.apply(new HttpRequest(HttpRequest.HttpMethod.GET, requestUri, Path.of(requestUri.getPath())));
        Assert.assertTrue(s.isPresent());
        Assert.assertEquals("A", s.get().get());

    }


    private static PathRoutes<String> paths() {
        return concat(path("/:a", s -> CompletableFuture.completedFuture("A")),
                      path("/:a/:b", s -> CompletableFuture.completedFuture("B")));
    }
}
