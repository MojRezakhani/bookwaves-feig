package de.bookwaves;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoggingConfigurationTest {
    @Test
    void yamlLevelsControlActualBackendAndPackageInheritance() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        var parent = context.getLogger("logging-test.package");
        var child = context.getLogger("logging-test.package.Child");
        Level originalRoot = root.getLevel();
        Level originalParent = parent.getLevel();
        Level originalChild = child.getLevel();
        try {
            child.setLevel(null);
            LoggingConfiguration.apply("warn", Map.of("logging-test.package", "debug"));
            assertEquals(Level.WARN, root.getEffectiveLevel());
            assertTrue(child.isDebugEnabled());
            assertFalse(root.isInfoEnabled());

            LoggingConfiguration.apply("DEBUG", Map.of("logging-test.package.Child", "ERROR"));
            assertTrue(root.isDebugEnabled());
            assertFalse(child.isWarnEnabled());
            assertTrue(child.isErrorEnabled());
        } finally {
            root.setLevel(originalRoot);
            parent.setLevel(originalParent);
            child.setLevel(originalChild);
        }
    }

    @Test
    void invalidLevelIsNotSilentlyTreatedAsDebug() {
        assertThrows(IllegalArgumentException.class,
            () -> LoggingConfiguration.apply("typo", Map.of()));
    }
}
