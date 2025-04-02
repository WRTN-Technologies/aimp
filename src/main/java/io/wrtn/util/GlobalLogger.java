package io.wrtn.util;

import org.jboss.logging.Logger;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.logmanager.formatters.PatternFormatter;

public class GlobalLogger {

    private static volatile GlobalLogger instance;
    private final Logger logger;

    private GlobalLogger(boolean isBatch) {
        if (isBatch) {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.INFO);

            PatternFormatter formatter = new PatternFormatter(
                "[%p] %s%e%n");
            handler.setFormatter(formatter);

            org.jboss.logmanager.Logger rootLogger = org.jboss.logmanager.Logger.getLogger(
                GlobalLogger.class.getName());
            rootLogger.addHandler(handler);
            rootLogger.setLevel(Level.INFO);
        }

        this.logger = Logger.getLogger(GlobalLogger.class);
    }

    public static void initialize(boolean isBatch) {
        if (instance == null) {
            synchronized (GlobalLogger.class) {
                if (instance == null) {
                    instance = new GlobalLogger(isBatch);
                }
            }
        }
    }

    private static void checkInitialization() {
        if (instance == null) {
            throw new IllegalStateException("GlobalLogger is not initialized");
        }
    }

    private static void log(LogLevel level, String message) {
        checkInitialization();
        switch (level) {
            case INFO -> instance.logger.info(message);
            case ERROR -> instance.logger.error(message);
            case WARN -> instance.logger.warn(message);
            case FATAL -> instance.logger.fatal(message);
            case DEBUG -> instance.logger.debug(message);
        }
    }

    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public static void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public static void fatal(String message) {
        log(LogLevel.FATAL, message);
    }

    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    private enum LogLevel {
        INFO, ERROR, WARN, FATAL, DEBUG
    }
}