package de.bookwaves;

import de.bookwaves.sync.ReaderMode;
import de.bookwaves.sync.ReaderProfile;
import de.bookwaves.sync.ReaderProfiles;
import de.bookwaves.sync.RssiFilterMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class representing an RFID reader, as read from {@code config.yaml}.
 *
 * <p>A plain holder. Which parameters a configuration implies, and what they are called
 * on a given generation of hardware, belongs to a {@link ReaderProfile}.
 */
public class ReaderConfig {

    private static Logger log() {
        return LoggerFactory.getLogger(ReaderConfig.class);
    }

    private String name;
    private String address;
    private int port;
    private Integer listenerPort;
    private String mode;
    private String protocol;
    private List<Integer> antennas = new ArrayList<>();
    private List<Integer> rssiFilters = new ArrayList<>();
    private String rssiFilterMode;
    private List<Double> outputPowers = new ArrayList<>();
    private String username;
    private String password;
    private String type = ReaderProfiles.GENERIC;
    private String transponderValidTime;
    private String persistenceResetTime;
    private RssiFilter rssiFilter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Integer getListenerPort() {
        return listenerPort;
    }

    public void setListenerPort(Integer listenerPort) {
        this.listenerPort = listenerPort;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getProtocol() {
        if (protocol == null || protocol.isBlank()) {
            return "uhf";
        }
        return protocol.trim().toLowerCase();
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /** The raw {@code type} value from configuration. */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = (type == null || type.isBlank()) ? ReaderProfiles.GENERIC : type.trim();
    }

    /**
     * The profile describing this reader's parameter set, or empty when its
     * configuration is not managed by this service.
     *
     * @throws IllegalArgumentException if {@code type} names no known profile
     */
    public Optional<ReaderProfile> getProfile() {
        return ReaderProfiles.find(type);
    }

    /** Whether this service synchronises this reader's configuration. */
    public boolean isManaged() {
        return getProfile().isPresent();
    }

    /** Whether this reader pushes tag reads rather than being polled. */
    public boolean isNotificationMode() {
        return ReaderMode.NOTIFICATION.matches(mode);
    }

    public boolean isHfProtocol() {
        return "hf".equalsIgnoreCase(getProtocol());
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank()
            && password != null && !password.isBlank();
    }

    /** Milliseconds before the same tag is reported again, {@code never}, or null for the default. */
    public String getTransponderValidTime() {
        return transponderValidTime;
    }

    public void setTransponderValidTime(String transponderValidTime) {
        this.transponderValidTime = transponderValidTime;
    }

    /** Milliseconds after a read before persistence flags reset, {@code never}, or null for the default. */
    public String getPersistenceResetTime() {
        return persistenceResetTime;
    }

    public void setPersistenceResetTime(String persistenceResetTime) {
        this.persistenceResetTime = persistenceResetTime;
    }

    public List<Integer> getAntennas() {
        return antennas;
    }

    public void setAntennas(List<Integer> antennas) {
        this.antennas = antennas == null ? new ArrayList<>() : new ArrayList<>(antennas);
    }

    public List<Integer> getRssiFilters() {
        return rssiFilters;
    }

    public void setRssiFilters(List<Integer> rssiFilters) {
        this.rssiFilters = rssiFilters == null ? new ArrayList<>() : new ArrayList<>(rssiFilters);
    }

    /** The raw {@code rssiFilterMode} value from configuration, or null for the default. */
    public String getRssiFilterMode() {
        return rssiFilterMode;
    }

    public void setRssiFilterMode(String rssiFilterMode) {
        this.rssiFilterMode = rssiFilterMode;
    }

    /**
     * Where this reader's {@code rssiFilters} are enforced.
     *
     * @throws IllegalArgumentException if {@code rssiFilterMode} names no known mode
     */
    public RssiFilterMode getEffectiveRssiFilterMode() {
        return RssiFilterMode.parse(rssiFilterMode);
    }

    /**
     * The RSSI filter (software defined) for this reader's reads. One instance per reader,
     * so its warnings are issued once rather than on every read.
     */
    public synchronized RssiFilter rssiFilter() {
        if (rssiFilter == null) {
            rssiFilter = RssiFilter.forReader(this);
        }
        return rssiFilter;
    }

    public List<Double> getOutputPowers() {
        return outputPowers;
    }

    public void setOutputPowers(List<Double> outputPowers) {
        this.outputPowers = outputPowers == null ? new ArrayList<>() : new ArrayList<>(outputPowers);
    }

    /**
     * Computes the antenna bitmask from the list of antenna numbers.
     * Antenna 1 = 0x01, Antenna 2 = 0x02, Antenna 3 = 0x04, etc.
     * Multiple antennas are combined with bitwise OR.
     *
     * @return the antenna bitmask as a byte
     */
    public byte getAntennaMask() {
        int mask = 0;
        for (Integer antenna : antennas) {
            if (antenna == null) {
                log().warn("Ignoring null antenna index for reader {}", name);
                continue;
            }
            if (antenna >= 1 && antenna <= 8) {
                mask |= (1 << (antenna - 1));
            } else {
                log().warn("Ignoring invalid antenna index {} for reader {}", antenna, name);
            }
        }
        log().debug("Computed antenna mask 0x{} for reader {} from {}", String.format("%02X", mask), name, antennas);
        return (byte) mask;
    }

    /**
     * Returns the effective antenna mask with protocol-specific behavior.
     * HF readers currently run on antenna 1 only.
     */
    public byte getEffectiveAntennaMask() {
        if (isHfProtocol()) {
            return 0x01;
        }
        return getAntennaMask();
    }
}
