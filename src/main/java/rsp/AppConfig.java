package rsp;

/**
 * An application configuration.
 */
public final class AppConfig {

    /**
     * The default rate of heartbeat messages from a browser to server.
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 10000;

    /**
     * The default configuration.
     */
    public static final AppConfig DEFAULT = new AppConfig(DEFAULT_HEARTBEAT_INTERVAL_MS);

    /**
     * The rate of heartbeat messages from a browser to server.
     */
    public final int heartbeatIntervalMs;

     /**
     * Creates an instance of an application object.
     * @param heartbeatIntervalMs the rate of heartbeat messages from a browser to server
     */
    public AppConfig(final int heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    /**
     * Creates a new copy of the configuration with a provided heartbeat interval.
     * @param heartbeatIntervalMs the rate of heartbeat messages from a browser to server
     * @return a new configuration object with the same field values except of the provided field
     */
    public AppConfig withHeartbeatIntervalMs(final int heartbeatIntervalMs) {
        return new AppConfig(heartbeatIntervalMs);
    }
}
