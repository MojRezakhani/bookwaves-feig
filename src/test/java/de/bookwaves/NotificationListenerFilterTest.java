package de.bookwaves;

import de.bookwaves.NotificationListener.NotificationEvent.AntennaRssi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationListenerFilterTest {
    private static NotificationListener listener() {
        ReaderConfig config = new ReaderConfig();
        config.setName("r");
        config.setAntennas(List.of(1, 2));
        config.setRssiFilters(List.of(40, 70));
        // Filtering and caching do not call the SDK; no reader or native TagItem is needed.
        return new NotificationListener(null, 10, new ReentrantLock(), config.getAntennas(),
            false, config.rssiFilter());
    }

    @Test
    void writeAntennaComesFromSurvivingReadings() {
        NotificationListener listener = listener();
        try {
            AntennaRssi rejected = new AntennaRssi(1, -50);
            AntennaRssi accepted = new AntennaRssi(2, -60);
            assertEquals(List.of(accepted),
                listener.filterObservation("epc", null, List.of(rejected, accepted)).orElseThrow());
            assertEquals(2, listener.getWriteTargetByEpc("EPC").antenna());
        } finally {
            listener.close();
        }
    }

    @Test
    void weakObservationRevokesWriteEligibilityAndStrongReadRestoresIt() {
        NotificationListener listener = listener();
        try {
            listener.filterObservation("epc", null, List.of(new AntennaRssi(2, -60)));
            assertNotNull(listener.getWriteTargetByEpc("epc"));
            assertTrue(listener.filterObservation("epc", null, List.of(new AntennaRssi(2, -80))).isEmpty());
            assertNull(listener.getWriteTargetByEpc("epc"));
            listener.filterObservation("epc", null, List.of(new AntennaRssi(2, -60)));
            assertNotNull(listener.getWriteTargetByEpc("epc"));
        } finally {
            listener.close();
        }
    }

    @Test
    void noValidRssiPassesButDisallowedAntennaRevokesWriteEligibility() {
        NotificationListener listener = listener();
        try {
            // The SDK extraction leaves this list empty for absent or all-invalid RSSI.
            assertEquals(List.of(), listener.filterObservation("epc", null, List.of()).orElseThrow());
            assertEquals(0, listener.getWriteTargetByEpc("epc").antenna());
            assertTrue(listener.filterObservation("epc", null, List.of(new AntennaRssi(3, -30))).isEmpty());
            assertNull(listener.getWriteTargetByEpc("epc"));
        } finally {
            listener.close();
        }
    }

    @Test
    void closeClearsWriteEligibility() {
        NotificationListener listener = listener();
        try {
            listener.filterObservation("epc", null, List.of(new AntennaRssi(2, -60)));
        } finally {
            listener.close();
        }
        assertNull(listener.getWriteTargetByEpc("epc"));
    }
}
