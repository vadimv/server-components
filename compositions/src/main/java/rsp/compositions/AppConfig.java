package rsp.compositions;

public record AppConfig(int port) {
    public static AppConfig DEFAULT = new AppConfig(8080);
}
