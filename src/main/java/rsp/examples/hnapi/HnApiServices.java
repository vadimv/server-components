package rsp.examples.hnapi;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HnApiServices {
    public CompletableFuture<List<Integer>> storiesIds() {
        return CompletableFuture.completedFuture(Arrays.asList(1, 2, 3));
    }

    public CompletableFuture<State.Story> story(int id) {
        return CompletableFuture.completedFuture(new State.Story(id,"Story #" + id));
    }
}
