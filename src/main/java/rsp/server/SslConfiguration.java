package rsp.server;

public class SslConfiguration {
    public final String keyStorePath;
    public final String keyStorePassword;

    public SslConfiguration(String keyStorePath, String keyStorePassword) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }
}
