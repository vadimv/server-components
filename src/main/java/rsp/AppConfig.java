package rsp;

import rsp.util.Log;

import static rsp.util.Log.DEFAULT_CONSOLE_LOG;

public final class AppConfig {

    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 10000;
    public static final int DEFAULT_SCHEDULER_THREAD_POOL_SIZE = 10;

    public static AppConfig DEFAULT = new AppConfig(DEFAULT_HEARTBEAT_INTERVAL_MS,
                                                    DEFAULT_SCHEDULER_THREAD_POOL_SIZE,
                                                    DEFAULT_CONSOLE_LOG);

    public final int heartbeatIntervalMs;
    public final int schedulerThreadPoolSize;
    public final Log.Reporting log;

    public AppConfig(int heartbeatIntervalMs,
                     int schedulerThreadPoolSize,
                     Log.Reporting log) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.schedulerThreadPoolSize = schedulerThreadPoolSize;
        this.log = log;
    }

    public AppConfig heartbeatIntervalMs(int heartbeatIntervalMs) {
        return new AppConfig(heartbeatIntervalMs,
                             this.schedulerThreadPoolSize,
                             this.log);
    }


    public AppConfig log(Log.Reporting log) {
        return new AppConfig(this.heartbeatIntervalMs,
                             this.schedulerThreadPoolSize,
                             log);
    }
}
