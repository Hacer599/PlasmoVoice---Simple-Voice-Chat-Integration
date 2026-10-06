package dev.faspadio.pvsvcbridge.client.connection;

public enum BridgeState {

    OFFLINE("pv-svc-bridge.status.offline"),
    PROBING("pv-svc-bridge.status.probing"),
    PLASMO_ACTIVE("pv-svc-bridge.status.plasmo_active"),
    SIMPLE_VOICE_CHAT_NATIVE("pv-svc-bridge.status.svc_native"),
    SIMPLE_VOICE_CHAT_BRIDGE("pv-svc-bridge.status.svc_bridge"),
    SIMPLE_VOICE_CHAT_UNAVAILABLE("pv-svc-bridge.status.svc_transport_unavailable"),
    UNAVAILABLE("pv-svc-bridge.status.unavailable");

    private final String translationKey;

    BridgeState(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
