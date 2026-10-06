package dev.faspadio.pvsvcbridge.common.config;

import dev.faspadio.pvsvcbridge.common.log.BridgeLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BridgeConfig {

    private static final List<String> DEFAULT_PLASMO_CHANNELS = List.of(
            "plasmo:voice/v2",
            "plasmo:voice/v2/service"
    );

    private static final List<String> DEFAULT_SVC_CHANNELS = List.of(
            "voicechat:request_secret",
            "voicechat:secret",
            "voicechat:update_state",
            "voicechat:player_states"
    );

    private String priority = BackendPreference.PLASMO.id();
    private boolean fallbackToSimpleVoiceChat = true;
    private boolean bridgeEnabled = true;
    private boolean allowDualBackend = false;
    private boolean debug = false;
    private boolean announceInChat = true;
    private volatile double simpleVoiceChatVolume = 1.0;
    private volatile Map<String, Double> simpleVoiceChatPlayerVolumes = new ConcurrentHashMap<>();
    private List<String> plasmoVoiceChannels = new ArrayList<>(DEFAULT_PLASMO_CHANNELS);
    private List<String> simpleVoiceChatChannels = new ArrayList<>(DEFAULT_SVC_CHANNELS);

    public BackendPreference priority() {
        return BackendPreference.byId(priority, BackendPreference.PLASMO);
    }

    public void setPriority(BackendPreference preference) {
        this.priority = preference.id();
    }

    public boolean fallbackToSimpleVoiceChat() {
        return fallbackToSimpleVoiceChat;
    }

    public void setFallbackToSimpleVoiceChat(boolean value) {
        this.fallbackToSimpleVoiceChat = value;
    }

    public boolean bridgeEnabled() {
        return bridgeEnabled;
    }

    public void setBridgeEnabled(boolean value) {
        this.bridgeEnabled = value;
    }

    public boolean allowDualBackend() {
        return allowDualBackend;
    }

    public void setAllowDualBackend(boolean value) {
        this.allowDualBackend = value;
    }

    public boolean debug() {
        return debug;
    }

    public void setDebug(boolean value) {
        this.debug = value;
    }

    public boolean announceInChat() {
        return announceInChat;
    }

    public void setAnnounceInChat(boolean value) {
        this.announceInChat = value;
    }

    public double simpleVoiceChatVolume() {
        return simpleVoiceChatVolume;
    }

    public void setSimpleVoiceChatVolume(double value) {
        simpleVoiceChatVolume = clampVolume(value);
    }

    public double simpleVoiceChatPlayerVolume(UUID playerId) {
        return simpleVoiceChatPlayerVolumes.getOrDefault(playerId.toString(), 1.0);
    }

    public void setSimpleVoiceChatPlayerVolume(UUID playerId, double value) {
        double volume = clampVolume(value);
        if (volume == 1.0) {
            simpleVoiceChatPlayerVolumes.remove(playerId.toString());
        } else {
            simpleVoiceChatPlayerVolumes.put(playerId.toString(), volume);
        }
    }

    public List<String> plasmoVoiceChannels() {
        return plasmoVoiceChannels;
    }

    public List<String> simpleVoiceChatChannels() {
        return simpleVoiceChatChannels;
    }

    void normalize() {
        priority = priority().id();
        simpleVoiceChatVolume = clampVolume(simpleVoiceChatVolume);
        if (simpleVoiceChatPlayerVolumes == null) {
            simpleVoiceChatPlayerVolumes = new ConcurrentHashMap<>();
        } else {
            Map<String, Double> cleanedVolumes = new HashMap<>();
            simpleVoiceChatPlayerVolumes.forEach((playerId, volume) -> {
                try {
                    UUID.fromString(playerId);
                    if (volume != null) {
                        cleanedVolumes.put(playerId, clampVolume(volume));
                    }
                } catch (IllegalArgumentException invalidPlayerId) {
                    BridgeLog.debug("Ignoring invalid SVC player volume entry {}", playerId);
                }
            });
            simpleVoiceChatPlayerVolumes = new ConcurrentHashMap<>(cleanedVolumes);
        }
        plasmoVoiceChannels = sanitize(plasmoVoiceChannels, DEFAULT_PLASMO_CHANNELS);
        simpleVoiceChatChannels = sanitize(simpleVoiceChatChannels, DEFAULT_SVC_CHANNELS);
    }

    private static double clampVolume(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(2.0, value));
    }

    private static List<String> sanitize(List<String> values, List<String> fallback) {
        if (values == null) {
            return new ArrayList<>(fallback);
        }
        List<String> cleaned = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        return cleaned.isEmpty() ? new ArrayList<>(fallback) : cleaned;
    }
}
