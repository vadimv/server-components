package rsp;

import rsp.util.Log;

import static rsp.util.Log.DEFAULT_CONSOLE_LOG;

/**
 * An application configuration
 */
public final class AppConfig {
    /**
     * The default rate of heartbeat messages from a browser to server
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 10000;

    /**
     * The application's scheduler thread pool default size
     */
    public static final int DEFAULT_SCHEDULER_THREAD_POOL_SIZE = 10;

    /**
     * The default configuration
     */
    public static AppConfig DEFAULT = new AppConfig(DEFAULT_HEARTBEAT_INTERVAL_MS,
                                                    DEFAULT_SCHEDULER_THREAD_POOL_SIZE,
                                                    DEFAULT_CONSOLE_LOG);

    /**
     * The rate of heartbeat messages from a browser to server
     */
    public final int heartbeatIntervalMs;

    /**
     * The application's scheduler thread pool size
     */
    public final int schedulerThreadPoolSize;

    /**
     * The application's logger
     */
    public final Log.Reporting log;

    /**
     * Creates an instance of an application object
     * @param heartbeatIntervalMs the rate of heartbeat messages from a browser to server
     * @param schedulerThreadPoolSize the application's scheduler thread pool size
     * @param log the application's logger
     */
    public AppConfig(int heartbeatIntervalMs,
                     int schedulerThreadPoolSize,
                     Log.Reporting log) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.schedulerThreadPoolSize = schedulerThreadPoolSize;
        this.log = log;
    }

    /**
     * Creates a new copy of the configuration with a provided heartbeat interval
     * @param heartbeatIntervalMs the rate of heartbeat messages from a browser to server
     * @return a new configuration object with the same field values except of the provided field
     */
    public AppConfig heartbeatIntervalMs(int heartbeatIntervalMs) {
        return new AppConfig(heartbeatIntervalMs,
                             this.schedulerThreadPoolSize,
                             this.log);
    }

    /**
     * Creates a new copy of the configuration with a provided application logger
     * @param log the application's logger
     * @return a new configuration object with the same field values except of the provided field
     */
    public AppConfig log(Log.Reporting log) {
        return new AppConfig(this.heartbeatIntervalMs,
                             this.schedulerThreadPoolSize,
                             log);
    }
}
