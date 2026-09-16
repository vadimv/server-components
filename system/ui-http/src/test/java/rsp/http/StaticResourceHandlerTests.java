package rsp.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticResourceHandlerTests {
    @TempDir
    Path resources;

    @Test
    void resolves_decoded_url_segments_without_treating_encoded_slashes_as_separators() throws Exception {
        Files.writeString(resources.resolve("hello world.txt"), "hello", StandardCharsets.UTF_8);
        StaticResourceHandler handler = new StaticResourceHandler(resources.toFile(), "/res/");

        HttpResponse response = handler.handle(rsp.url.Path.parse("/res/hello%20world.txt"));

        assertEquals(HttpStatus.OK, response.status());
        assertEquals("hello", new String(response.body().openStream().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals(HttpStatus.NOT_FOUND,
                handler.handle(rsp.url.Path.parse("/res/nested%2Fsecret.txt")).status());
    }

    @Test
    void rejects_directory_traversal() {
        StaticResourceHandler handler = new StaticResourceHandler(resources.toFile(), "/res/");

        assertEquals(HttpStatus.FORBIDDEN,
                handler.handle(rsp.url.Path.parse("/res/../outside.txt")).status());
    }
}
