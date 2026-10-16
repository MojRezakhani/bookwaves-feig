package de.bookwaves;

import de.bookwaves.sync.ConfigurationSync;
import de.bookwaves.sync.FeigReaderConfigPort;
import de.bookwaves.sync.ParamSpec;
import de.bookwaves.sync.ParamValue;
import de.bookwaves.sync.ProtectedParameters;
import de.bookwaves.sync.ReaderConfigPort;
import de.bookwaves.sync.ReaderProfile;
import de.bookwaves.sync.SyncReport;

import de.feig.fedm.ReaderModule;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves against real readers that a reader ends up matching its configuration, that
 * drift is noticed, and that only what drifted is written.
 *
 * <p>Run with {@code mvn test -Phardware}. Nothing here needs a human or a tag.
 *
 * <p><b>Every write is permanent.</b> Neither generation has a RAM-only configuration
 * bank, so a run that crashes between mutating and restoring leaves the reader changed.
 * The mutation prints the value it displaces first, so it can be undone by hand.
 */
@Tag("hardware")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HardwareSyncTest {

    /** The parameter mutation tests move: per-antenna and harmless. */
    private static final String MUTABLE_PARAMETER = "AirInterface.Antenna.UHF.No%d.RSSIFilter";

    static List<Named<ReaderConfig>> readers() throws Exception {
        return HardwareReaders.managed().stream()
            .map(config -> Named.of(config.getName(), config))
            .toList();
    }

    @Order(1)
    @ParameterizedTest(name = "{0}")
    @MethodSource("readers")
    @DisplayName("the reader converges on its configuration")
    void converges(ReaderConfig config) throws Exception {
        ReaderModule module = HardwareReaders.connect(config);
        try {
            ConfigurationSync sync = syncFor(config);
            ReaderConfigPort port = new FeigReaderConfigPort(module, config.getName());

            SyncReport repaired = sync.apply(port, config, false);
            System.out.println(repaired.summary());

            SyncReport after = sync.check(port, config);
            assertTrue(after.inSync(), "still drifted after a repair: " + after.summary());
        } finally {
            HardwareReaders.close(module);
        }
    }

    @Order(2)
    @ParameterizedTest(name = "{0}")
    @MethodSource("readers")
    @DisplayName("drift is detected, only the drifted parameter is written, and it is restored")
    void detectsAndRepairsOnlyWhatDrifted(ReaderConfig config) throws Exception {
        ReaderModule module = HardwareReaders.connect(config);
        try {
            ConfigurationSync sync = syncFor(config);
            ReaderConfigPort port = new FeigReaderConfigPort(module, config.getName());

            Assumptions.assumeTrue(sync.check(port, config).inSync(),
                "reader " + config.getName() + " does not match its configuration yet");

            String parameter = String.format(MUTABLE_PARAMETER, config.getAntennas().get(0));
            ParamValue desired = desiredValueOf(config, parameter);
            ParamValue mutated = nudge(desired);

            System.out.println("Mutating " + parameter + " from " + desired.describe()
                + " to " + mutated.describe() + "; write it back by hand if this run stops here");

            port.set(parameter, mutated);
            port.apply(true);

            SyncReport drifted = sync.check(port, config);
            assertEquals(1, drifted.drifts().size(), "expected one drift: " + drifted.summary());
            assertEquals(parameter, drifted.drifts().get(0).spec().name());

            SyncReport repaired = sync.apply(port, config, false);
            assertEquals(List.of(parameter), repaired.written(),
                "a repair wrote more than the drifted parameter: " + repaired.summary());

            // Back where it started.
            assertEquals(desired, port.get(parameter, desired.type()));
            assertTrue(sync.check(port, config).inSync());
        } finally {
            HardwareReaders.close(module);
        }
    }

    @Order(3)
    @ParameterizedTest(name = "{0}")
    @MethodSource("readers")
    @DisplayName("no reader identity or credential is ever written")
    void writesNoProtectedParameter(ReaderConfig config) throws Exception {
        ReaderProfile profile = config.getProfile().orElseThrow();

        for (ParamSpec spec : profile.parametersFor(config, HardwareReaders.hostName())) {
            assertFalse(ProtectedParameters.isProtected(spec.name()),
                "the " + profile.id() + " profile would write " + spec.name());
        }
    }

    @Order(4)
    @ParameterizedTest(name = "{0}")
    @MethodSource("readers")
    @DisplayName("a reader still reads and writes after a forced disconnect")
    void survivesForcedDisconnect(ReaderConfig config) throws Exception {
        String parameter = String.format(MUTABLE_PARAMETER, config.getAntennas().get(0));
        ParamValue desired = desiredValueOf(config, parameter);

        ReaderModule module = HardwareReaders.connect(config);
        ParamValue before;
        try {
            before = new FeigReaderConfigPort(module, config.getName()).get(parameter, desired.type());
        } finally {
            HardwareReaders.close(module);
        }

        module = HardwareReaders.connect(config);
        try {
            ReaderConfigPort port = new FeigReaderConfigPort(module, config.getName());
            port.readCompleteConfiguration();
            assertEquals(before, port.get(parameter, desired.type()));

            // A write needs the credentials the reconnect reapplied. The value is
            // unchanged, so a refusal is the only thing this can report.
            port.set(parameter, before);
            port.apply(true);
        } finally {
            HardwareReaders.close(module);
        }
    }

    @Order(5)
    @ParameterizedTest(name = "{0}")
    @MethodSource("readers")
    @DisplayName("managed host operations synchronise on first use and after reconnect")
    void managedConnectionsRepairDrift(ReaderConfig config) throws Exception {
        Assumptions.assumeFalse(config.isNotificationMode(), "host connection lifecycle only");
        String parameter = String.format(MUTABLE_PARAMETER, config.getAntennas().get(0));
        ParamValue desired = desiredValueOf(config, parameter);
        // Preserve the hardware harness's skip behavior for unavailable readers/native libraries.
        HardwareReaders.close(HardwareReaders.connect(config));
        ReaderManager.ManagedReader managed = new ReaderManager.ManagedReader(config);
        try {
            ReaderModule module = managed.getModule();
            ReaderConfigPort port = new FeigReaderConfigPort(module, config.getName());
            port.readCompleteConfiguration();
            assertEquals(desired, port.get(parameter, desired.type()));
            assertEquals(ReaderManager.SyncState.IN_SYNC, managed.getSyncState());

            port.set(parameter, nudge(desired));
            port.apply(true);
            managed.forceReconnect();
            port = new FeigReaderConfigPort(managed.getModule(), config.getName());
            port.readCompleteConfiguration();
            assertEquals(desired, port.get(parameter, desired.type()));

            port.set(parameter, nudge(desired));
            port.apply(true);
            managed.disconnect();
            port = new FeigReaderConfigPort(managed.getModule(), config.getName());
            port.readCompleteConfiguration();
            assertEquals(desired, port.get(parameter, desired.type()));
        } finally {
            try {
                managed.syncConfiguration(false);
            } finally {
                managed.close();
            }
        }
    }

    private static ConfigurationSync syncFor(ReaderConfig config) throws Exception {
        return new ConfigurationSync(
            config.getProfile().orElseThrow(), HardwareReaders.hostName(), true);
    }

    /** What the configuration asks this parameter to be. */
    private static ParamValue desiredValueOf(ReaderConfig config, String parameter) throws Exception {
        return config.getProfile().orElseThrow()
            .parametersFor(config, HardwareReaders.hostName()).stream()
            .filter(spec -> spec.name().equals(parameter))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no profile writes " + parameter))
            .desired();
    }

    /** A value the reader can hold that is not the configured one. */
    private static ParamValue nudge(ParamValue desired) {
        long value = ((ParamValue.Numeric) desired).value();
        return ParamValue.ofByte((int) (value == 0 ? 1 : value - 1));
    }
}
