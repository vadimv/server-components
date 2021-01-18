package rsp.server;

/**
 * A basic SSL/TLS configuration.
 * For a self-signed certificate, run the Java keytool utility, for example:
 * keytool -genkey -alias sitename -keyalg RSA -keystore keystore.jks -keysize 2048
 */
public final class SslConfiguration {
    /**
     * A path to a Java keystore file.
     */
    public final String keyStorePath;
    /**
     * A keystore password.
     */
    public final String keyStorePassword;

    /**
     * Creates a new instance of a SSL/TLS configuration.
     * @param keyStorePath a path to a keystore.jks file
     * @param keyStorePassword a keystore password
     */
    public SslConfiguration(String keyStorePath, String keyStorePassword) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }
}
