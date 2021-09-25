package rsp.routing;

import rsp.server.Path;
import rsp.util.data.Tuple2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathPattern {

    public final String[] patternSegments;
    public final int[] paramsIndexes;
    private final Map<Integer, Pattern> regexes;

    private static final Pattern regex = Pattern.compile("^\\.*(.*)$");

    PathPattern(String[] patternSegments, int[] paramsIndexes, Map<Integer, Pattern> regexes) {
        this.patternSegments = patternSegments;
        this.paramsIndexes = paramsIndexes;
        this.regexes = regexes;
    }

    public static PathPattern of(String pattern) {
        final String[] segments = Arrays.stream(pattern.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        return new PathPattern(segments, paramsIndexes(segments), null);
    }

    private static Tuple2<Path, Map<Integer, Pattern>> parse(String pattern) {
        final String[] segments = pattern.split("/");

        for (String segment: segments) {

        }
        final Matcher m = regex.matcher(pattern);

        return new Tuple2<>(Path.of(pattern), null);
    }

    private static int[] paramsIndexes(String[] pattern) {
        final List<Integer> l = new ArrayList<>();
        for (int i = 0; i < pattern.length; i++) {
            if (isParam(pattern[i])) {
                l.add(i);
            }
        }
        return l.stream().mapToInt(i -> i).toArray();
    }

    public boolean match(Path path) {
        if (path.isEmpty() && patternSegments.length == 0) {
            return true;
        }
        if (!path.isEmpty() && patternSegments.length == 0) {
            return false;
        }

        int i;
        for(i = 0; i < path.size() && i < patternSegments.length; i++) {
            if (!(path.get(i).equals(patternSegments[i])
                    || isWildcard(patternSegments[i])
                    || isParam(patternSegments[i]))) {
                return false;
            }
        }
        return (path.size() == patternSegments.length)
                || (path.size() > patternSegments.length && isWildcard(patternSegments[i -1])) ;
    }

    private static boolean isParam(String str) {
        return str.startsWith(":");
    }

    private static boolean isWildcard(String str) {
        return str.equals("*");
    }
}
