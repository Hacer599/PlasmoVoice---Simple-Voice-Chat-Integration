package dev.faspadio.pvsvcbridge.client.connection;

import dev.faspadio.pvsvcbridge.common.config.BridgeConfig;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import dev.faspadio.pvsvcbridge.compat.detect.ServerVoiceSupport;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class ServerVoiceProbe {

    private ServerVoiceProbe() {
    }

    public static ServerVoiceSupport probe(BridgeConfig config) {
        boolean plasmoVoice = anyChannelAccepted(config.plasmoVoiceChannels());
        boolean simpleVoiceChat = anyChannelAccepted(config.simpleVoiceChatChannels());
        return new ServerVoiceSupport(plasmoVoice, simpleVoiceChat);
    }

    private static boolean anyChannelAccepted(List<String> channels) {
        for (String raw : channels) {
            Identifier channel = Identifier.tryParse(raw);
            if (channel == null) {
                BridgeLog.warn("Skipping malformed channel id from config: {}", raw);
                continue;
            }
            try {
                if (ClientPlayNetworking.canSend(channel)) {
                    BridgeLog.debug("Server accepts channel {}", channel);
                    return true;
                }
            } catch (IllegalStateException notConnected) {
                BridgeLog.debug("Channel probe skipped, no active play connection", notConnected);
                return false;
            }
        }
        return false;
    }

    public static void logServerChannels() {
        if (!BridgeLog.isDebug()) {
            return;
        }
        try {
            List<String> names = new ArrayList<>();
            for (Identifier channel : ClientPlayNetworking.getSendable()) {
                names.add(channel.toString());
            }
            names.sort(String::compareTo);
            BridgeLog.debug("Channels accepted by the server: {}", String.join(", ", names));
        } catch (IllegalStateException notConnected) {
            BridgeLog.debug("Channel list unavailable, no active play connection", notConnected);
        }
    }
}
