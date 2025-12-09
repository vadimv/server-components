package rsp.server.http;

/**
 * Represents an HTTP header.
 * @see HttpRequest
 * @see HttpResponse
 * @param name
 * @param value
 */
public record Header(String name, String value) {
}
