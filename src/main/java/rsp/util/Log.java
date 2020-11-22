package rsp.util;

import java.util.function.Consumer;

public interface Log {

    Reporting DEFAULT = new Default(Level.TRACE, new SimpleFormat(), string -> System.out.println(string) );

    void log(String message);

    void log(String message, Throwable cause);

    enum Level {
        TRACE, DEBUG, INFO, WARNING, ERROR
    }

    interface Format {
        String format(Level level, String message);
        String format(Level level, String message, Throwable cause);
    }

    interface Reporting {
        void trace(Consumer<Log> logConsumer);
        void debug(Consumer<Log> logConsumer);
        void info(Consumer<Log> logConsumer);
        void warning(Consumer<Log> logConsumer);
        void error(Consumer<Log> logConsumer);
    }

    class SimpleFormat implements Format {

        @Override
        public String format(Level level, String message) {
            return "[" + level.name() + "] " + message;
        }

        @Override
        public String format(Level level, String message, Throwable cause) {
            return "[" + level.name() + "] " + message + "\n"
                    + cause.getMessage() + "\n"
                    + ExceptionsUtils.stackTraceToString(cause);
        }
    }

    class Default implements Reporting {

        private final Level level;
        private final Log traceLog;
        private final Log debugLog;
        private final Log infoLog;
        private final Log warningLog;
        private final Log errorLog;

        public Default(Level level, Format format, Consumer<String> out) {
            this.level = level;
            this.traceLog = new LogImpl(Level.TRACE, format, out);
            this.debugLog = new LogImpl(Level.DEBUG, format, out);
            this.infoLog = new LogImpl(Level.INFO, format, out);
            this.warningLog = new LogImpl(Level.WARNING, format, out);
            this.errorLog = new LogImpl(Level.ERROR, format, out);
        }

        @Override
        public void trace(Consumer<Log> logConsumer) {
            if (level == Level.TRACE) logConsumer.accept(traceLog);
        }

        @Override
        public void debug(Consumer<Log> logConsumer) {
            if (level == Level.TRACE || level == Level.DEBUG) logConsumer.accept(debugLog);
        }

        @Override
        public void info(Consumer<Log> logConsumer) {
            if (level == Level.TRACE
                || level == Level.DEBUG
                || level == Level.INFO) logConsumer.accept(infoLog);
        }

        @Override
        public void warning(Consumer<Log> logConsumer) {
            if (level == Level.TRACE
                || level == Level.DEBUG
                || level == Level.INFO
                || level == Level.WARNING) logConsumer.accept(warningLog);
        }

        @Override
        public void error(Consumer<Log> logConsumer) {
            logConsumer.accept(errorLog);
        }

        private class LogImpl implements Log {
            private final Level level;
            private final Format format;
            private final Consumer<String> out;

            private LogImpl(Level level, Format format, Consumer<String> out) {
                this.level = level;
                this.format = format;
                this.out = out;
            }

            @Override
            public void log(String message) {
                out.accept(format.format(level, message));
            }

            @Override
            public void log(String message, Throwable cause) {
                out.accept(format.format(level, message, cause));
            }
        }
    }
}
