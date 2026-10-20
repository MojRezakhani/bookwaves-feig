package de.bookwaves;

import de.bookwaves.NotificationListener.NotificationEvent.AntennaRssi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationListenerAntennaTest {

    private static AntennaRssi seen(int antenna, int rssi) {
        return new AntennaRssi(antenna, rssi);
    }

    @Test
    @DisplayName("the one antenna that saw the tag is the one to write on")
    void singleAntenna() {
        assertEquals(2, NotificationListener.strongestAntenna(List.of(seen(2, -55))));
    }

    @Test
    @DisplayName("the strongest of several antennas wins")
    void strongestOfSeveral() {
        assertEquals(1, NotificationListener.strongestAntenna(
            List.of(seen(2, -70), seen(1, -48), seen(4, -61))));
    }

    @Test
    @DisplayName("a reader that reports no signal strength still names an antenna")
    void noRssiStillPicksAnAntenna() {
        assertEquals(3, NotificationListener.strongestAntenna(List.of(seen(3, 0), seen(4, 0))));
    }

    @Test
    void positiveMagnitudesChooseSmallestAndPreserveRawBestRssi() {
        var readings = List.of(seen(2, 57), seen(1, 30), seen(4, 42));
        assertEquals(1, NotificationListener.strongestAntenna(readings));
        assertEquals(30, NotificationListener.bestRssi(readings));
        assertEquals(-30, NotificationListener.bestRssi(List.of(seen(1, -30), seen(2, -57))));
        assertEquals(1, NotificationListener.strongestAntenna(List.of(seen(2, 57), seen(1, -30))));
    }

    @Test
    @DisplayName("no antenna reported means no antenna to write on")
    void nothingReported() {
        assertEquals(0, NotificationListener.strongestAntenna(List.of()));
    }
}
