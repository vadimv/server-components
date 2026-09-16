package rsp.http;

import org.junit.jupiter.api.Test;
import rsp.url.Path;
import rsp.url.Query;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpContractsTests {
    @Test
    void headers_are_case_insensitive_and_preserve_repeated_values() {
        HttpHeaders headers = HttpHeaders.builder()
                .add("X-Trace", "first")
                .add("x-trace", "second")
                .build();

        assertEquals("first", headers.first("X-TRACE").orElseThrow());
        assertEquals(java.util.List.of("first", "second"), headers.all("x-Trace"));
    }

    @Test
    void request_bodies_are_bounded_and_defensively_copied() {
        byte[] bytes = "body".getBytes(StandardCharsets.UTF_8);
        RequestBody body = RequestBody.of(bytes, bytes.length);
        bytes[0] = 'x';

        assertEquals("body", body.text());
        assertThrows(RequestBody.BodyLimitExceededException.class,
                () -> RequestBody.of(new byte[5], 4));
    }

    @Test
    void text_responses_use_utf8_and_redirects_preserve_the_location() throws Exception {
        HttpResponse text = HttpResponse.ok().text("żółw").build();
        HttpResponse redirect = HttpResponse.redirect(URI.create("/next?value=a%20b"));

        assertArrayEquals("żółw".getBytes(StandardCharsets.UTF_8), text.body().openStream().readAllBytes());
        assertEquals(MediaType.TEXT_UTF_8.toString(), text.headers().first("Content-Type").orElseThrow());
        assertEquals(HttpStatus.FOUND, redirect.status());
        assertEquals("/next?value=a%20b", redirect.headers().first("location").orElseThrow());
    }

    @Test
    void cookies_are_emitted_as_repeated_headers() {
        HttpResponse response = HttpResponse.ok()
                .cookie(SetCookie.of("session", "abc")
                        .path("/")
                        .maxAge(Duration.ofMinutes(5))
                        .withHttpOnly()
                        .sameSite(SetCookie.SameSite.LAX))
                .cookie(SetCookie.of("theme", "dark"))
                .build();

        assertEquals(java.util.List.of(
                "session=abc; Path=/; Max-Age=300; HttpOnly; SameSite=Lax",
                "theme=dark"), response.headers().all("Set-Cookie"));
    }

    @Test
    void streamed_bodies_expose_the_declared_length() throws Exception {
        ResponseBody body = ResponseBody.stream(
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}), OptionalLong.of(3));

        assertEquals(3, body.contentLength().orElseThrow());
        assertArrayEquals(new byte[]{1, 2, 3}, body.openStream().readAllBytes());
    }

    @Test
    void requests_preserve_the_raw_target() {
        HttpRequest request = new HttpRequest(
                HttpMethod.GET,
                "/items/a%2Fb?tag=one&tag=two",
                "/items/a%2Fb",
                URI.create("/items/a%2Fb?tag=one&tag=two"),
                "http://localhost/items/a%2Fb?tag=one&tag=two",
                Path.parse("/items/a%2Fb"),
                Query.parse("tag=one&tag=two"),
                HttpHeaders.EMPTY,
                RequestBody.EMPTY);

        assertEquals("/items/a%2Fb?tag=one&tag=two", request.rawTarget());
        assertEquals("a/b", request.path().get(1));
        assertEquals(java.util.List.of("one", "two"), request.query().parameterValues("tag"));
    }
}
