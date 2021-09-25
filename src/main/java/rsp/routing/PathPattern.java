package rsp.routing;

import rsp.server.Path;

import java.util.ArrayList;
import java.util.List;

public class PathPattern {

    public final Path pattern;
    public final int[] paramsIndexes;

    public PathPattern(Path pattern, int[] paramsIndexes) {
        this.pattern = pattern;
        this.paramsIndexes = paramsIndexes;
    }

    public static PathPattern of(String pattern) {
        final Path p = Path.of(pattern);
        return new PathPattern(p, paramsIndexes(p));
    }

    public boolean match(Path path) {
        if (path.isEmpty() && pattern.isEmpty()) {
            return true;
        }
        if (!path.isEmpty() && pattern.isEmpty()) {
            return false;
        }

        int i;
        for(i = 0; i < path.size() && i < pattern.size(); i++) {
            if (!(pattern.get(i).equals(pattern.get(i))
                    || pattern.get(i).equals("*")
                    || isParam(pattern.get(i)))) {
                return false;
            }
        }
        return (path.size() == pattern.size())
                || (path.size() > pattern.size() && pattern.get(i -1).equals("*")) ;
    }

    private static int[] paramsIndexes(Path pattern) {
        final List<Integer> l = new ArrayList<>();
        for (int i = 0; i < pattern.size(); i++) {
            if (isParam(pattern.get(i))) {
                l.add(i);
            }
        }
        return l.stream().mapToInt(i -> i).toArray();
    }

    private static boolean isParam(String patternSegment) {
        return patternSegment.startsWith(":");
    }
}
