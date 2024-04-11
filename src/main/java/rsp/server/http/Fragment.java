package rsp.server.http;

public record Fragment(String fragmentString) {
    public static final Fragment EMPTY = new Fragment("");
}
