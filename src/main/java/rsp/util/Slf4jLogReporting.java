package rsp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * A Slf4j logger.
 */
public final class Slf4jLogReporting implements Log.Reporting {

    private final Logger logger;

    private final Log traceLog;
    private final Log debugLog;
    private final Log infoLog;
    private final Log warnLog;
    private final Log errorLog;

    public Slf4jLogReporting(String name) {
        logger = LoggerFactory.getLogger(name);

        traceLog = new Log() {
            @Override
            public void log(String message) {
                logger.trace(message);
            }

            @Override
            public void log(String message, Throwable ex) {
                logger.trace(message, ex);
            }
        };

        debugLog = new Log() {
            @Override
            public void log(String message) {
                logger.debug(message);
            }

            @Override
            public void log(String message, Throwable ex) {
                logger.debug(message, ex);
            }
        };

        infoLog = new Log() {
            @Override
            public void log(String message) {
                logger.info(message);
            }

            @Override
            public void log(String message, Throwable ex) {
                logger.info(message, ex);
            }
        };

        warnLog = new Log() {
            @Override
            public void log(String message) {
                logger.warn(message);
            }

            @Override
            public void log(String message, Throwable ex) {
                logger.warn(message, ex);
            }
        };

        errorLog = new Log() {
            @Override
            public void log(String message) {
                logger.error(message);
            }

            @Override
            public void log(String message, Throwable ex) {
                logger.error(message, ex);
            }
        };
    }

    @Override
    public void trace(Consumer<Log> logConsumer) {
        if (logger.isTraceEnabled()) logConsumer.accept(traceLog);
    }

    @Override
    public void debug(Consumer<Log> logConsumer) {
        if (logger.isDebugEnabled()) logConsumer.accept(debugLog);
    }

    @Override
    public void info(Consumer<Log> logConsumer) {
        if (logger.isInfoEnabled()) logConsumer.accept(infoLog);
    }

    @Override
    public void warn(Consumer<Log> logConsumer) {
        if (logger.isWarnEnabled()) logConsumer.accept(warnLog);
    }

    @Override
    public void error(Consumer<Log> logConsumer) {
        if (logger.isErrorEnabled()) logConsumer.accept(errorLog);
    }
}
