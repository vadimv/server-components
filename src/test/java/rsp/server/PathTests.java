package rsp.server;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;


public class PathTests {
    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(Path.class).verify();
    }

    @Test
    public void should_correctly_create_new_empty_relative_path_from_empty_string() {
        final Path path = Path.of("");
        assertEquals(Path.EMPTY_RELATIVE, path);
    }

    private void assertEquals(Path emptyRelative, Path path) {
    }

    @Test
    public void should_correctly_create_new_empty_absolute_from_string() {
        final Path path = Path.of("/");
        assertEquals(Path.EMPTY_ABSOLUTE, path);
    }

    @Test
    public void should_correctly_create_new_relative_path_from_string() {
        final Path path = Path.of("foo/bar");
        assertEquals(new Path(false, "foo", "bar"), path);
    }

    @Test
    public void should_correctly_create_new_absolute_from_string() {
        final Path path = Path.of("/foo/bar");
        assertEquals(new Path(true, "foo", "bar"), path);
    }
}
