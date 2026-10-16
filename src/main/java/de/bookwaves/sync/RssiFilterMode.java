package de.bookwaves.sync;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Where a reader's {@code rssiFilters} are enforced. Never both: see ADR 0002.
 */
public enum RssiFilterMode {
    /** This service discards weak readings, and a managed reader's own filter is off. */
    SOFTWARE("software"),
    /** The reader discards weak readings itself, and this service filters nothing. */
    READER("reader");

    private final String configValue;

    RssiFilterMode(String configValue) {
        this.configValue = configValue;
    }

    /** The value written in {@code config.yaml}. */
    public String configValue() {
        return configValue;
    }

    /** Whether {@code mode} names this mode, ignoring case and surrounding space. */
    public boolean matches(String mode) {
        return mode != null && configValue.equalsIgnoreCase(mode.trim());
    }

    /**
     * Parses an {@code rssiFilterMode} value from configuration. Unlike {@link ReaderMode},
     * the key is optional: unset means {@link #SOFTWARE}.
     *
     * @throws IllegalArgumentException naming the supported modes, if unrecognised
     */
    public static RssiFilterMode parse(String mode) {
        if (mode == null || mode.isBlank()) {
            return SOFTWARE;
        }
        for (RssiFilterMode candidate : values()) {
            if (candidate.matches(mode)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
            "Unsupported rssiFilterMode '" + mode.trim() + "'; supported modes are " + supportedValues());
    }

    /** The supported mode values, for error messages. */
    public static String supportedValues() {
        return Arrays.stream(values())
            .map(mode -> mode.configValue.toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(", "));
    }
}
