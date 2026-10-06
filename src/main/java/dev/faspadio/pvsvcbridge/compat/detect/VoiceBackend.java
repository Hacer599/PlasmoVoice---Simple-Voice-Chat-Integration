package dev.faspadio.pvsvcbridge.compat.detect;

public enum VoiceBackend {

    PLASMO_VOICE("Plasmo Voice"),
    SIMPLE_VOICE_CHAT("Simple Voice Chat"),
    NONE("none");

    private final String displayName;

    VoiceBackend(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
