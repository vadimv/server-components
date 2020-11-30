package rsp;

import rsp.util.Log;

public final class AppConfig {

    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 5000;
    public static final int DEFAULT_WEB_SERVER_MAX_THREADS = 50;
    public static final int DEFAULT_SCHEDULER_THREAD_POOL_SIZE = 10;
    public static final Log.Reporting DEFAULT_LOG = new Log.Default(Log.Level.TRACE, new Log.SimpleFormat(), string -> System.out.println(string));

    public static AppConfig DEFAULT = new AppConfig(DEFAULT_HEARTBEAT_INTERVAL_MS,
                                                    DEFAULT_WEB_SERVER_MAX_THREADS,
                                                    DEFAULT_SCHEDULER_THREAD_POOL_SIZE,
                                                    DEFAULT_LOG);

    public final int heartbeatIntervalMs;
    public final int webServerMaxThreads;
    public final int schedulerThreadPoolSize;
    public final Log.Reporting log;

    public AppConfig(int heartbeatIntervalMs,
                     int webServerMaxThreads,
                     int schedulerThreadPoolSize,
                     Log.Reporting log) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.webServerMaxThreads = webServerMaxThreads;
        this.schedulerThreadPoolSize = schedulerThreadPoolSize;
        this.log = log;
    }

    public AppConfig heartbeatIntervalMs(int heartbeatIntervalMs) {
        return new AppConfig(heartbeatIntervalMs,
                             this.webServerMaxThreads,
                             this.schedulerThreadPoolSize,
                             this.log);
    }

    public AppConfig webServerMaxThreads(int webServerMaxThreads) {
        return new AppConfig(this.heartbeatIntervalMs,
                             webServerMaxThreads,
                             this.schedulerThreadPoolSize,
                             this.log);
    }

    public AppConfig log(Log.Reporting log) {
        return new AppConfig(this.heartbeatIntervalMs,
                             this.webServerMaxThreads,
                             this.schedulerThreadPoolSize,
                             log);
    }
}
