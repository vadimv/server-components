package rsp.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

public interface Log {
    enum Level {
        TRACE, DEBUG, INFO, WARNING, ERROR
    }

    void log(String message);

    void log(String message, Throwable cause);

    interface Out {
        void write(String string);
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

    class Stout implements Out {

        @Override
        public void write(String string) {
            System.out.println(string);
        }
    }

    class SimpleFormat implements Format {

        @Override
        public String format(Level level, String message) {
            return "[" + level.name() + "]" + message;
        }

        @Override
        public String format(Level level, String message, Throwable cause) {
            return "[" + level.name() + "] " + message + "\n"
                    + cause.getMessage() + "\n"
                    + stackTrace(cause);
        }
    }

    static String stackTrace(Throwable ex) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    class Default implements Reporting {
        private final Log traceLog;
        private final Log debugLog;
        private final Log infoLog;
        private final Log warningLog;
        private final Log errorLog;

        public Default(Format format, Consumer<String> out) {
            this.traceLog = new LogImpl(Level.TRACE, format, out);
            this.debugLog = new LogImpl(Level.DEBUG, format, out);
            this.infoLog = new LogImpl(Level.INFO, format, out);
            this.warningLog = new LogImpl(Level.WARNING, format, out);
            this.errorLog = new LogImpl(Level.ERROR, format, out);
        }

        @Override
        public void trace(Consumer<Log> logConsumer) {
            logConsumer.accept(traceLog);
        }

        @Override
        public void debug(Consumer<Log> logConsumer) {
            logConsumer.accept(debugLog);
        }

        @Override
        public void info(Consumer<Log> logConsumer) {
            logConsumer.accept(infoLog);
        }

        @Override
        public void warning(Consumer<Log> logConsumer) {
            logConsumer.accept(warningLog);
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
