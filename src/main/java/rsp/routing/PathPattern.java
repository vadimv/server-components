package rsp.routing;

import rsp.server.Path;

public class PathPattern {

    private final String pattern;

    public PathPattern(String pattern) {
        this.pattern = pattern;
    }

    public boolean match(Path path) {
        return false;
    }

    public int[] paramsIndexes() {
        return new int[0];
    }
}
