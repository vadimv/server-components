package rsp.server;

import org.junit.jupiter.api.Test;
import rsp.server.http.Header;
import rsp.server.http.HttpMethod;
import rsp.server.http.HttpRequest;
import rsp.server.http.Query;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestTests {
    @Test
    void should_parse_cookies_header_correctly() throws URISyntaxException {
        final String cookieHeader = "Cookie: name=value; name2=value2; name3=value3";
        final URI uri = new URI("http://foo/bar");
        final HttpRequest request = new HttpRequest(HttpMethod.GET,
                                                    uri,
                                                    uri.toString(),
                                                    Path.EMPTY,
                                                    Query.EMPTY,
                                                    List.of(new Header("Cookie", "name=value; name2=value2; name3=value3")));

        final Optional<String> cookieValue = request.cookies("name2").stream().findFirst();
        assertEquals(Optional.of("value2"), cookieValue);
    }


    @Test
    void should_parse_cookies_header_correctly_for_empty() throws URISyntaxException {
        final String cookieHeader = "Cookie: name=value; name2=value2; name3=value3";
        final URI uri = new URI("http://foo/bar");
        final HttpRequest request = new HttpRequest(HttpMethod.GET,
                                                    uri,
                                                    uri.toString(),
                                                    Path.EMPTY,
                                                    Query.EMPTY,
                                                    List.of(new Header("Cookie", "name=value; name2=value2; name3=value3")));

        final Optional<String> cookieValue = request.cookies("name4").stream().findFirst();
        assertEquals(Optional.empty(), cookieValue);
    }
}
