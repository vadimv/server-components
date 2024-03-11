package rsp.server;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


public class PathTests {
    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(Path.class).verify();
    }

    @Test
    public void should_correctly_create_new_empty_relative_path_from_empty_string() {
        final Path path = Path.of("");
        assertFalse(path.isAbsolute());
        assertTrue(path.isEmpty());
        assertEquals(Path.EMPTY, path);
    }

    @Test
    public void should_correctly_create_new_empty_absolute_from_string() {
        final Path path = Path.of("/");
        assertTrue(path.isAbsolute());
        assertFalse(path.isEmpty());
        assertEquals(Path.ROOT, path);
    }

    @Test
    public void should_correctly_create_new_relative_path_from_string() {
        final Path path = Path.of("foo/bar");
        assertFalse(path.isAbsolute());
        final String[] expectedElements = {"foo", "bar"};
        assertArrayEquals(expectedElements, path.elements());
    }

    @Test
    public void should_correctly_create_new_absolute_from_string() {
        final Path path = Path.of("/foo/bar");
        assertTrue(path.isAbsolute());
        final String[] expectedElements = {"foo", "bar"};
        assertArrayEquals(expectedElements, path.elements());
    }

}
