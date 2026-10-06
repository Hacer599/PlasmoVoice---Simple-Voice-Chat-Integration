package dev.faspadio.pvsvcbridge.client;

import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.client.connection.BridgeState;
import su.plo.voice.api.client.PlasmoVoiceClient;

public final class PlasmoVoiceAddonLoader {

    private static final PlasmoVoiceBridgeAddon ADDON = new PlasmoVoiceBridgeAddon();

    private PlasmoVoiceAddonLoader() {
    }

    public static void register() {
        PlasmoVoiceClient.getAddonsLoader().load(ADDON);
    }

    public static void tick() {
        PlasmoVoiceSettings.tick();
        if (BridgeSession.get().state() == BridgeState.SIMPLE_VOICE_CHAT_BRIDGE) {
            PlasmoVoiceSettings.ensureBridgeActivation();
        }
    }
}
