package rsp.routing;

import rsp.server.Path;
import rsp.util.data.Tuple2;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class PathPattern {
    public final List<String> patternSegments;
    public final int[] paramsIndexes;
    private final Map<Integer, Pattern> regexes;

    private static final Pattern findRegex = Pattern.compile("\\(.*\\)");

    PathPattern(List<String> patternSegments, int[] paramsIndexes, Map<Integer, Pattern> regexes) {
        this.patternSegments = patternSegments;
        this.paramsIndexes = paramsIndexes;
        this.regexes = regexes;
    }

    public static PathPattern of(String pattern) {
        final Tuple2<List<String>, Map<Integer, Pattern>> p = parse(pattern);
        return new PathPattern(p._1, paramsIndexes(p._1), p._2);
    }

    public static Tuple2<List<String>, Map<Integer, Pattern>> parse(String pattern) {
        final String[] segments = Arrays.stream(pattern.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        final List<String> l = new ArrayList<>();
        final Map<Integer, Pattern> regexMap = new HashMap<>();
        for (int i = 0; i < segments.length; i++ ) {
            final String segment = segments[i];
            if (isParam(segment)) {
                final Matcher m = findRegex.matcher(segment);
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
        return new Tuple2<>(l, regexMap);
    }

    private static int[] paramsIndexes(List<String> patternSegments) {
        final List<Integer> l = new ArrayList<>();
        for (int i = 0; i < patternSegments.size(); i++) {
            if (isParam(patternSegments.get(i))) {
                l.add(i);
            }
        }
        return l.stream().mapToInt(i -> i).toArray();
    }

    public boolean match(Path path) {
        if (path.isEmpty() && patternSegments.size() == 0) {
            return true;
        }
        if (!path.isEmpty() && patternSegments.size() == 0) {
            return false;
        }

        int i;
        for(i = 0; i < path.size() && i < patternSegments.size(); i++) {
            if (!(path.get(i).equals(patternSegments.get(i))
                    || isWildcard(patternSegments.get(i))
                    || (isParam(patternSegments.get(i))
                        && (regexes.get(i) == null || regexes.get(i).matcher(path.get(i)).find())))) {
                return false;
            }
        }
        return (path.size() == patternSegments.size())
                || (path.size() > patternSegments.size() && isWildcard(patternSegments.get(i -1))) ;
    }

    private static boolean isParam(String str) {
        return str.startsWith(":");
    }

    private static boolean isWildcard(String str) {
        return str.equals("*");
    }
}
