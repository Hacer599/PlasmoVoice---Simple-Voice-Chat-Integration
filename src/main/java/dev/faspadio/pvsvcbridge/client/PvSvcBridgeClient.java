package dev.faspadio.pvsvcbridge.client;

import dev.faspadio.pvsvcbridge.PvSvcBridge;
import dev.faspadio.pvsvcbridge.client.command.BridgeCommands;
import dev.faspadio.pvsvcbridge.client.connection.SimpleVoiceChatClientBridge;
import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import dev.faspadio.pvsvcbridge.compat.detect.InstalledMods;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class PvSvcBridgeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BridgeLog.info("Client ready, backend priority is {}", PvSvcBridge.config().priority().id());
        SimpleVoiceChatClientBridge.initialize();

        boolean plasmoVoiceMenuAvailable = InstalledMods.plasmoVoiceAddonApiAvailable();
        if (plasmoVoiceMenuAvailable) {
            PlasmoVoiceAddonLoader.register();
        } else if (InstalledMods.plasmoVoice()) {
            BridgeLog.warn("Plasmo Voice 2.1.16 or newer is required for the add-ons menu integration");
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BridgeSession.get().onConnect());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BridgeSession.get().onDisconnect();
            PlasmoVoiceSettings.clearSpeakerWidgets();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (plasmoVoiceMenuAvailable) {
                PlasmoVoiceAddonLoader.tick();
            }
            SimpleVoiceChatClientBridge.tick();
            BridgeSession.get().tick();
            PlasmoVoiceMenuHandler.tick();
        });

        BridgeCommands.register();
    }
}
