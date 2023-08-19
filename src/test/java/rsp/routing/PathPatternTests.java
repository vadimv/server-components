package rsp.routing;

import org.junit.jupiter.api.Test;
import rsp.server.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PathPatternTests {

    @Test
    public void should_match_empty_path_for_empty_pattern() {
        assertTrue(PathPattern.of("").match(Path.of("")));
    }

    @Test
    public void should_not_match_non_empty_path_for_empty_pattern() {
        assertFalse(PathPattern.of("").match(Path.of("/1")));
    }

    @Test
    public void should_not_match_non_empty_path_for_non_empty_pattern() {
        assertFalse(PathPattern.of("/1").match(Path.of("")));
    }

    @Test
    public void should_match_path_for_pattern() {
        assertTrue(PathPattern.of("/1/2").match(Path.of("/1/2")));
    }

    @Test
    public void should_not_match_for_path_shorter_with_slash() {
        assertFalse(PathPattern.of("/1/2").match(Path.of("/1/")));
    }

    @Test
    public void should_not_match_for_path_shorter_than_pattern() {
        assertFalse(PathPattern.of("/1/2").match(Path.of("/1")));
    }

    @Test
    public void should_match_path_for_pattern_with_wildcard() {
        final PathPattern pathPattern = PathPattern.of("/1/*");
        assertTrue(pathPattern.match(Path.of("/1/2")));
        assertTrue(pathPattern.match(Path.of("/1/2/3")));
    }

    @Test
    public void should_not_match_path_for_pattern_with_wildcard() {
        assertFalse(PathPattern.of("/1/2/*").match(Path.of("/1/")));
    }

    @Test
    public void should_match_path_for_pattern_with_params() {
        assertTrue(PathPattern.of("/:p1/2/:p2").match(Path.of("/1/2/3")));
    }

    @Test
    public void should_not_match_path_for_pattern_with_params() {
        assertFalse(PathPattern.of("/:p1/1/").match(Path.of("/1/2/3")));
    }

    @Test
    public void should_match_path_for_pattern_with_params_and_wildcards() {
        assertTrue(PathPattern.of("/:p1/2/:p2/*").match(Path.of("/1/2/3/4/5")));
    }

    @Test
    public void should_match_path_for_pattern_with_params_and_regex() {
        assertTrue(PathPattern.of("/1/:p1(^\\d+$)/3").match(Path.of("/1/2/3")));
        assertTrue(PathPattern.of("/1/:p1(^XYZ$)/3").match(Path.of("/1/XYZ/3")));
        assertTrue(PathPattern.of("/1/:p1(^XYZ$)/3/:p2(^\\d+$)").match(Path.of("/1/XYZ/3/4")));
    }

    @Test
    public void should_not_match_path_for_pattern_with_params_and_regex() {
        assertFalse(PathPattern.of("/1/:p1(^\\d+$)/3").match(Path.of("/1/A/3")));
        assertFalse(PathPattern.of("/1/:p1(^XYZ$)/3").match(Path.of("/1/XYZ0/3")));
    }

    @Test
    public void should_correctly_get_path_parameters() {
        final PathPattern pathPattern = PathPattern.of("/:p1(^\\d+$)/2/:p2");
        final Path path = Path.of("/1/2/3");
        assertTrue(pathPattern.match(path));
        assertEquals(2, pathPattern.paramsIndexes.length);
        assertEquals("1", path.get(pathPattern.paramsIndexes[0]));
        assertEquals("3", path.get(pathPattern.paramsIndexes[1]));
    }
}
