package rsp.routing;

import org.junit.Assert;
import org.junit.Test;
import rsp.server.Path;

public class PathPatternTests {

    @Test
    public void should_match_empty_path_for_empty_pattern() {
        Assert.assertTrue(PathPattern.of("").match(Path.of("")));
    }

    @Test
    public void should_not_match_non_empty_path_for_empty_pattern() {
        Assert.assertFalse(PathPattern.of("").match(Path.of("/1")));
    }

    @Test
    public void should_not_match_non_empty_path_for_non_empty_pattern() {
        Assert.assertFalse(PathPattern.of("/1").match(Path.of("")));
    }

    @Test
    public void should_match_path_for_pattern() {
        Assert.assertTrue(PathPattern.of("/1/2").match(Path.of("/1/2")));
    }

    @Test
    public void should_not_match_for_path_shorter_with_slash() {
        Assert.assertFalse(PathPattern.of("/1/2").match(Path.of("/1/")));
    }

    @Test
    public void should_not_match_for_path_shorter_than_pattern() {
        Assert.assertFalse(PathPattern.of("/1/2").match(Path.of("/1")));
    }

    @Test
    public void should_match_path_for_pattern_with_wildcard() {
        final PathPattern pathPattern = PathPattern.of("/1/*");
        Assert.assertTrue(pathPattern.match(Path.of("/1/2")));
        Assert.assertTrue(pathPattern.match(Path.of("/1/2/3")));
    }

    @Test
    public void should_not_match_path_for_pattern_with_wildcard() {
        Assert.assertFalse(PathPattern.of("/1/2/*").match(Path.of("/1/")));
    }

    @Test
    public void should_match_path_for_pattern_with_params() {
        Assert.assertTrue(PathPattern.of("/:p1/1/:p2").match(Path.of("/1/2/3")));
    }

    @Test
    public void should_not_match_path_for_pattern_with_params() {
        Assert.assertFalse(PathPattern.of("/:p1/1/").match(Path.of("/1/2/3")));
    }

    @Test
    public void should_match_path_for_pattern_with_params_and_wildcards() {
        Assert.assertTrue(PathPattern.of("/:p1/1/:p2/*").match(Path.of("/1/2/3/4/5")));
    }

    @Test
    public void should_correctly_get_path_parameters() {
        final PathPattern pathPattern = PathPattern.of("/:p1/2/:p2");
        final Path path = Path.of("/1/2/3");
        Assert.assertTrue(pathPattern.match(path));
        Assert.assertEquals(2, pathPattern.paramsIndexes.length);
        Assert.assertEquals("1", path.get(pathPattern.paramsIndexes[0]));
        Assert.assertEquals("3", path.get(pathPattern.paramsIndexes[1]));
    }
}
