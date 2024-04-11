package rsp.server.http;

public record Query(String queryString) {
    public static final Query EMPTY = new Query("");
}
