package de.bookwaves;

import de.bookwaves.sync.RssiFilterMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RSSI filter (software defined): a per-antenna minimum signal strength applied to a
 * tag's readings after the reader has reported them. See ADR 0002.
 *
 * <p>Filters are configured in -dBm, as the reader's own filter is, while readings are
 * either signed dBm or positive -dBm magnitudes (observed on MRU102 notifications).
 * A filter of 60 keeps readings of -60 or +60 and stronger; 0 keeps everything.
 */
public final class RssiFilter {

    private static final Logger log = LoggerFactory.getLogger(RssiFilter.class);

    /** The configured value that switches filtering off for an antenna. */
    private static final int NO_FILTER = 0;

    private final String readerName;
    private final Map<Integer, Integer> minimumByAntenna;
    private final AtomicBoolean warnedAboutMissingRssi = new AtomicBoolean(false);

    private RssiFilter(String readerName, Map<Integer, Integer> minimumByAntenna) {
        this.readerName = readerName;
        this.minimumByAntenna = Map.copyOf(minimumByAntenna);
    }

    /** The filter a reader's configuration asks for. */
    public static RssiFilter forReader(ReaderConfig config) {
        Map<Integer, Integer> minimumByAntenna = new HashMap<>();
        if (config.getEffectiveRssiFilterMode() != RssiFilterMode.SOFTWARE) {
            return new RssiFilter(config.getName(), minimumByAntenna);
        }
        List<Integer> antennas = config.getAntennas();
        List<Integer> rssiFilters = config.getRssiFilters();
        for (int i = 0; i < Math.min(antennas.size(), rssiFilters.size()); i++) {
            int rssiFilter = rssiFilters.get(i);
            if (rssiFilter != NO_FILTER) {
                minimumByAntenna.put(antennas.get(i), -rssiFilter);
            }
        }
        return new RssiFilter(config.getName(), minimumByAntenna);
    }

    /**
     * The readings of one tag that survive, or empty when the tag is discarded.
     *
     * @param epc      the tag, for logging
     * @param readings the tag's readings, one per antenna that heard it
     * @param antenna  the antenna a reading came from
     * @param rssi     a reading's raw signal strength (signed dBm or positive magnitude)
     */
    public <T> Optional<List<T>> filter(String epc, List<T> readings,
                                        ToIntFunction<T> antenna, ToIntFunction<T> rssi) {
        if (minimumByAntenna.isEmpty()) {
            return Optional.of(readings);
        }
        if (readings.isEmpty()) {
            // Nothing to judge. Dropping it would hide every tag a misconfigured reader reports.
            if (warnedAboutMissingRssi.compareAndSet(false, true)) {
                log.warn("Reader {} reported {} without a signal strength; the RSSI filter lets such tags through",
                    readerName, epc);
            }
            return Optional.of(List.of());
        }

        List<T> surviving = new ArrayList<>();
        for (T reading : readings) {
            int readingAntenna = antenna.applyAsInt(reading);
            int readingRssi = rssi.applyAsInt(reading);
            Integer minimum = minimumByAntenna.get(readingAntenna);
            boolean keep = minimum == null || signedDbm(readingRssi) >= minimum;
            if (keep) {
                surviving.add(reading);
            } else {
                log.debug("Reader {}: dropped reading of {} on antenna {} at {} dBm (raw RSSI {}, RSSI filter {} dBm)",
                    readerName, epc, readingAntenna, signedDbm(readingRssi), readingRssi, minimum);
            }
        }
        return surviving.isEmpty() ? Optional.empty() : Optional.of(surviving);
    }

    /** Normalizes for comparison only; API readings retain the SDK's raw value. */
    static int signedDbm(int raw) {
        return raw > 0 ? -raw : raw;
    }
}
