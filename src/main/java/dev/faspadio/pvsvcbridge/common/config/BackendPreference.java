package dev.faspadio.pvsvcbridge.common.config;

import java.util.Locale;

public enum BackendPreference {

    PLASMO("plasmo"),
    SIMPLE_VOICE_CHAT("simple_voice_chat");

    private final String id;

    BackendPreference(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "pv-svc-bridge.priority." + id;
    }

    public BackendPreference opposite() {
        return this == PLASMO ? SIMPLE_VOICE_CHAT : PLASMO;
    }

    public static BackendPreference byId(String value, BackendPreference fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "plasmo", "plasmo_voice", "plasmovoice", "pv" -> PLASMO;
            case "simple_voice_chat", "simplevoicechat", "svc", "voicechat" -> SIMPLE_VOICE_CHAT;
            default -> fallback;
        };
    }
}
