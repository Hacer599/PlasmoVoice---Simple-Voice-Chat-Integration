package dev.faspadio.pvsvcbridge.compat.detect;

public record ServerVoiceSupport(boolean plasmoVoice, boolean simpleVoiceChat) {

    public static final ServerVoiceSupport UNKNOWN = new ServerVoiceSupport(false, false);

    public boolean any() {
        return plasmoVoice || simpleVoiceChat;
    }

    public boolean both() {
        return plasmoVoice && simpleVoiceChat;
    }
}
