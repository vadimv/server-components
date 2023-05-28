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
     * The application's scheduler thread pool default size.
     */
    public static final int DEFAULT_SCHEDULER_THREAD_POOL_SIZE = 10;

    /**
     * The default configuration.
     */
    public static AppConfig DEFAULT = new AppConfig(DEFAULT_HEARTBEAT_INTERVAL_MS,
                                                    DEFAULT_SCHEDULER_THREAD_POOL_SIZE);

    /**
     * The rate of heartbeat messages from a browser to server.
     */
    public final int heartbeatIntervalMs;

    /**
     * The application's scheduler thread pool size.
     */
    public final int schedulerThreadPoolSize;

    /**
     * Creates an instance of an application object.
     * @param heartbeatIntervalMs the rate of heartbeat messages from a browser to server
     * @param schedulerThreadPoolSize the application's scheduler thread pool size
     */
    public AppConfig(final int heartbeatIntervalMs,
                     final int schedulerThreadPoolSize) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.schedulerThreadPoolSize = schedulerThreadPoolSize;
    }

    /**
     * Creates a new copy of the configuration with a provided heartbeat interval.
     * @param heartbeatIntervalMs the rate of heartbeat messages from a browser to server
     * @return a new configuration object with the same field values except of the provided field
     */
    public AppConfig heartbeatIntervalMs(final int heartbeatIntervalMs) {
        return new AppConfig(heartbeatIntervalMs,
                             this.schedulerThreadPoolSize);
    }
}
