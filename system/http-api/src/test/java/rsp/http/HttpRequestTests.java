package rsp.http;

import org.junit.jupiter.api.Test;
import rsp.url.Path;
import rsp.url.Query;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpRequestTests {
    @Test
    void should_parse_cookies_header_correctly() throws URISyntaxException {
        final URI uri = new URI("http://foo/bar");
        final HttpRequest request = new HttpRequest(HttpMethod.GET,
                                                    "/bar",
                                                    "/bar",
                                                    uri,
                                                    uri.toString(),
                                                    Path.EMPTY,
                                                    Query.EMPTY,
                                                    HttpHeaders.of(new HttpHeader("Cookie", "name=value; name2=value2; name3=value3")),
                                                    RequestBody.EMPTY);

        final String cookieValue = request.cookies("name2").stream().findFirst().orElse(null);
        assertEquals("value2", cookieValue);
    }


    @Test
    void should_parse_cookies_header_correctly_for_empty() throws URISyntaxException {
        final URI uri = new URI("http://foo/bar");
        final HttpRequest request = new HttpRequest(HttpMethod.GET,
                                                    "/bar",
                                                    "/bar",
                                                    uri,
                                                    uri.toString(),
                                                    Path.EMPTY,
                                                    Query.EMPTY,
                                                    HttpHeaders.of(new HttpHeader("Cookie", "name=value; name2=value2; name3=value3")),
                                                    RequestBody.EMPTY);

        final String cookieValue = request.cookies("name4").stream().findFirst().orElse(null);
        assertNull(cookieValue);
    }
}
