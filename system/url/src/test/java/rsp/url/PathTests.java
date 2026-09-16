package rsp.url;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class PathTests {

    @Test
    void should_correctly_create_new_empty_relative_path_from_empty_string() {
        final Path path = Path.of("");
        assertFalse(path.isAbsolute());
        assertTrue(path.isEmpty());
        assertEquals(Path.EMPTY, path);
    }

    @Test
    void should_correctly_create_new_empty_absolute_from_string() {
        final Path path = Path.of("/");
        assertTrue(path.isAbsolute());
        assertTrue(path.isEmpty());
        assertEquals(Path.ROOT, path);
    }

    @Test
    void should_correctly_create_new_relative_path_from_string() {
        final Path path = Path.of("foo/bar");
        assertFalse(path.isAbsolute());
        final String[] expectedElements = {"foo", "bar"};
        assertArrayEquals(expectedElements, path.elements());
    }

    @Test
    void should_correctly_create_new_absolute_from_string() {
        final Path path = Path.of("/foo/bar");
        assertTrue(path.isAbsolute());
        final String[] expectedElements = {"foo", "bar"};
        assertArrayEquals(expectedElements, path.elements());
    }

    @Test
    void parses_percent_encoded_segments_without_form_semantics() {
        Path path = Path.parse("/caf%C3%A9/a%2Fb/c+d");

        assertArrayEquals(new String[]{"café", "a/b", "c+d"}, path.elements());
        assertEquals("/caf%C3%A9/a%2Fb/c+d", path.toString());
    }

    @Test
    void rejects_malformed_percent_escapes() {
        assertThrows(IllegalArgumentException.class, () -> Path.parse("/bad%2"));
        assertThrows(IllegalArgumentException.class, () -> Path.parse("/bad%xx"));
    }

    @Test
    void defensively_copies_elements() {
        String[] source = {"one"};
        Path path = Path.fromElements(true, source);
        source[0] = "changed";
        String[] returned = path.elements();
        returned[0] = "changed-again";

        assertEquals("one", path.get(0));
    }

}
