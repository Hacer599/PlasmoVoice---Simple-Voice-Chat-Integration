package dev.faspadio.pvsvcbridge.client;

import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import dev.faspadio.pvsvcbridge.common.text.BridgeTexts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class BridgeChat {

    private BridgeChat() {
    }

    public static void send(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        client.execute(() -> {
            try {
                client.inGameHud.getChatHud().addMessage(
                        BridgeTexts.literal("[PV-SVC-Bridge] ").formatted(Formatting.GRAY).append(message)
                );
            } catch (RuntimeException error) {
                BridgeLog.debug("Could not print a chat message", error);
            }
        });
    }
}
