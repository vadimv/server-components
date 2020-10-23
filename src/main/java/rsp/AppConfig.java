package rsp;

public final class AppConfig {

    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 5000;
    public static final int DEFAULT_WEB_SERVER_MAX_THREADS = 50;
    public static final int DEFAULT_SCHEDULER_THREAD_POOL_SIZE = 10;

    public static AppConfig DEFAULT = new AppConfig(DEFAULT_HEARTBEAT_INTERVAL_MS,
                                                    DEFAULT_WEB_SERVER_MAX_THREADS,
                                                    DEFAULT_SCHEDULER_THREAD_POOL_SIZE);

    public final int heartbeatIntervalMs;
    public final int webServerMaxThreads;
    public final int schedulerThreadPoolSize;

    public AppConfig(int heartbeatIntervalMs,
                     int webServerMaxThreads,
                     int schedulerThreadPoolSize) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.webServerMaxThreads = webServerMaxThreads;
        this.schedulerThreadPoolSize = schedulerThreadPoolSize;
    }

    public AppConfig heartbeatIntervalMs(int heartbeatIntervalMs) {
        return new AppConfig(heartbeatIntervalMs,
                             this.webServerMaxThreads,
                             this.schedulerThreadPoolSize);
    }

    public AppConfig webServerMaxThreads(int webServerMaxThreads) {
        return new AppConfig(this.heartbeatIntervalMs,
                             webServerMaxThreads,
                             this.schedulerThreadPoolSize);
    }
}
