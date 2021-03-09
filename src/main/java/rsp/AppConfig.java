package rsp;

import rsp.util.logging.Log;

/**
 * An application configuration.
 */
public final class AppConfig {

    /**
     * Defines if auto HTML head tag upgrade is enabled.
     */
    public enum HtmlHeadUpgradeMode {
        /**
         * The RSP scripts tags added to the document's head tag.
         * This is the default rendering mode.
         */
        AUTO,

        /**
         * No HTML tags upgrade applied.
         */
        OFF
    }

    /**
     * The application's internal log level system property name, {@value LOG_LEVEL_PROPERTY_NAME}, {@link Log.Level},
     * the log level name can be provided in a lower or upper case.
     * If log levels do not have a constant with a matching name, an {@link IllegalArgumentException} will be thrown on initialization.
     * If no such property provided then a default log level will be used {@link #DEFAULT_LOG_LEVEL}.
     */
    public final static String LOG_LEVEL_PROPERTY_NAME = "rsp.log.level";

    /**
     * The default log level.
     */
    public final static Log.Level DEFAULT_LOG_LEVEL = Log.Level.INFO;

    /**
     * A console logger used by default.
     */
    public final static Log.Reporting DEFAULT_CONSOLE_LOG = new Log.Default(Log.Level.valueOf(System.getProperty(LOG_LEVEL_PROPERTY_NAME,
                                                                                                                 DEFAULT_LOG_LEVEL.name()).toUpperCase()),
                                                                                               new Log.SimpleFormat(),
                                                                                               string -> System.out.println(string));

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
    public static AppConfig DEFAULT = new AppConfig(HtmlHeadUpgradeMode.AUTO,
                                                    DEFAULT_HEARTBEAT_INTERVAL_MS,
                                                    DEFAULT_SCHEDULER_THREAD_POOL_SIZE,
                                                    DEFAULT_CONSOLE_LOG);

    /**
     * The HTML rendering mode.
     */
    public final HtmlHeadUpgradeMode htmlHeadUpgradeMode;

    /**
     * The rate of heartbeat messages from a browser to server.
     */
    public final int heartbeatIntervalMs;

    /**
     * The application's scheduler thread pool size.
     */
    public final int schedulerThreadPoolSize;

    /**
     * The application's logger.
     */
    public final Log.Reporting log;

    /**
     * Creates an instance of an application object.
     * @param heartbeatIntervalMs the rate of heartbeat messages from a browser to server
     * @param schedulerThreadPoolSize the application's scheduler thread pool size
     * @param log the application's logger
     */
    public AppConfig(HtmlHeadUpgradeMode htmlHeadUpgradeMode,
                     int heartbeatIntervalMs,
                     int schedulerThreadPoolSize,
                     Log.Reporting log) {
        this.htmlHeadUpgradeMode = htmlHeadUpgradeMode;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.schedulerThreadPoolSize = schedulerThreadPoolSize;
        this.log = log;
    }

    public AppConfig htmlHeadUpgradeMode(HtmlHeadUpgradeMode htmlHeadUpgradeMode) {
        return new AppConfig(htmlHeadUpgradeMode,
                             this.heartbeatIntervalMs,
                             this.schedulerThreadPoolSize,
                             this.log);
    }


    /**
     * Creates a new copy of the configuration with a provided heartbeat interval.
     * @param heartbeatIntervalMs the rate of heartbeat messages from a browser to server
     * @return a new configuration object with the same field values except of the provided field
     */
    public AppConfig heartbeatIntervalMs(int heartbeatIntervalMs) {
        return new AppConfig(this.htmlHeadUpgradeMode,
                             heartbeatIntervalMs,
                             this.schedulerThreadPoolSize,
                             this.log);
    }

    /**
     * Creates a new copy of the configuration with a provided application logger.
     * @param log the application's logger
     * @return a new configuration object with the same field values except of the provided field
     */
    public AppConfig log(Log.Reporting log) {
        return new AppConfig(this.htmlHeadUpgradeMode,
                             this.heartbeatIntervalMs,
                             this.schedulerThreadPoolSize,
                             log);
    }

}
