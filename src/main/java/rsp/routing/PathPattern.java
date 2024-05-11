package rsp.routing;

import rsp.server.Path;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a componentPath matching pattern.
 */
public final class PathPattern {
    public final List<String> patternSegments;
    public final int[] paramsIndexes;

    private final Map<Integer, Pattern> regexes;

    private static final Pattern FIND_REGEX = Pattern.compile("\\(.*\\)");

    private PathPattern(final List<String> patternSegments, final int[] paramsIndexes, final Map<Integer, Pattern> regexes) {
        this.patternSegments = patternSegments;
        this.paramsIndexes = paramsIndexes;
        this.regexes = regexes;
    }

    /**
     * Creates a new instance of a componentPath pattern from a string.
     * The string can be empty or consist of explicit match segments, wildcard symbols "*" and up to two parameters definitions:
     * A componentPath parameter definition consists of ":", parameter name and optional regular expression in parentheses.
     * For example:
     *
     * "/segment1/segment2"
     * "/segment1/*"
     * "/segment1/:param1/:param2"
     * "/segment1/:param1/:param2(^\\d+$)"
     *
     * @param pattern the componentPath pattern
     * @return a result componentPath pattern
     */
    public static PathPattern of(final String pattern) {
        return parse(pattern);
    }

    private static PathPattern parse(final String pattern) {
        final String[] segments = Arrays.stream(pattern.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        final List<String> l = new ArrayList<>();
        final Map<Integer, Pattern> regexMap = new HashMap<>();
        for (int i = 0; i < segments.length; i++ ) {
            final String segment = segments[i];
            if (isParam(segment)) {
                final Matcher m = FIND_REGEX.matcher(segment);
                if (m.find()) {
                    final String extractedStr = m.group(0);
                    final String r = extractedStr.substring(1, extractedStr.length() - 1);
                    final Pattern p = Pattern.compile(r);
                    regexMap.put(i, p);
                    final String paramName = segment.substring(0, segment.indexOf(extractedStr));
                    l.add(paramName);
                    continue;
                }
            }
            l.add(segment);
        }
        return new PathPattern(l, paramsIndexes(l), regexMap);
    }

    private static int[] paramsIndexes(final List<String> patternSegments) {
        final List<Integer> l = new ArrayList<>();
        for (int i = 0; i < patternSegments.size(); i++) {
            if (isParam(patternSegments.get(i))) {
                l.add(i);
            }
        }
        return l.stream().mapToInt(i -> i).toArray();
    }

    public boolean match(final Path path) {
        if (path.isEmpty() && (patternSegments.isEmpty() || isWildcard(patternSegments.get(0)))) {
            return true;
        }
        if (!path.isEmpty() && patternSegments.isEmpty()) {
            return false;
        }

        int i;
        for(i = 0; i < path.size() && i < patternSegments.size(); i++) {
            if (!(path.get(i).equals(patternSegments.get(i))
                    || isWildcard(patternSegments.get(i))
                    || (isParam(patternSegments.get(i)) && (regexes.get(i) == null || regexes.get(i).matcher(path.get(i)).find())))) {
                return false;
            }
        }
        return (path.size() == patternSegments.size())
                || (path.size() > patternSegments.size() && isWildcard(patternSegments.get(i -1))) ;
    }

    private static boolean isParam(final String str) {
        return str.startsWith(":");
    }

    private static boolean isWildcard(final String str) {
        return str.equals("*");
    }
}
