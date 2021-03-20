package rsp.server;

import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class HttpRequestTests {
    @Test
    public void should_parse_cookies_header_correctly() throws URISyntaxException {
        final String cookieHeader = "Cookie: name=value; name2=value2; name3=value3";
        final HttpRequest request = new HttpRequest(HttpRequest.Methods.GET,
                                                    new URI("http://foo/bar"),
                                                    Path.EMPTY_RELATIVE,
                                                    s -> Optional.empty(),
                                                    s -> Optional.of(cookieHeader));

        final Optional<String> cookieValue = request.getCookie().apply("name2");
        Assert.assertEquals(Optional.of("value2"), cookieValue);
    }


    @Test
    public void should_parse_cookies_header_correctly_for_empty() throws URISyntaxException {
        final String cookieHeader = "Cookie: name=value; name2=value2; name3=value3";
        final HttpRequest request = new HttpRequest(HttpRequest.Methods.GET,
                                                    new URI("http://foo/bar"),
                                                    Path.EMPTY_RELATIVE,
                                                    s -> Optional.empty(),
                                                    s -> Optional.of(cookieHeader));

        final Optional<String> cookieValue = request.getCookie().apply("name4");
        Assert.assertEquals(Optional.empty(), cookieValue);
    }
}
