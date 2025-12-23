package rsp.server;

import java.util.Objects;

/**
 * A basic SSL/TLS configuration.
 * For a self-signed certificate, run the Java keytool utility, for example:
 * keytool -genkey -alias sitename -keyalg RSA -keystore keystore.jks -keysize 2048
 *
 * @param keyStorePath a componentPath to a Java keystore file, must be not null
 * @param keyStorePassword a keystore password, must be not null
 */
public record SslConfiguration(String keyStorePath, String keyStorePassword) {
    public SslConfiguration {
        Objects.requireNonNull(keyStorePath);
        Objects.requireNonNull(keyStorePassword);
    }
}
