package dev.faspadio.pvsvcbridge.client;

import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;

@Addon(
        id = "pv-svc-bridge",
        name = "SVC",
        version = "1.0.0",
        authors = {"faspadio - hoziain_murki"}
)
public final class PlasmoVoiceBridgeAddon implements AddonInitializer {

    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    @Override
    public void onAddonInitialize() {
        PlasmoVoiceSettings.register(voiceClient, voiceClient.getAddonConfig(this));
        BridgeLog.info("Plasmo Voice settings registered in the add-ons menu");
    }
}
