package de.bookwaves;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Applies YAML logging levels to the application's Logback backend. */
final class LoggingConfiguration {
    private LoggingConfiguration() {}

    static void apply(String rootLevel, Map<String, String> overrides) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(parseLevel(rootLevel));
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String name = entry.getKey();
            String level = entry.getValue();
            if (name == null || name.isBlank() || level == null || level.isBlank()) {
                continue;
            }
            context.getLogger(name.trim()).setLevel(parseLevel(level));
        }
    }

    private static Level parseLevel(String value) {
        if (value == null || value.isBlank()) {
            return Level.INFO;
        }
        Level level = Level.toLevel(value.trim(), null);
        if (level == null) {
            throw new IllegalArgumentException("Unsupported log level '" + value
                + "'; use TRACE, DEBUG, INFO, WARN, ERROR, OFF or ALL");
        }
        return level;
    }
}
