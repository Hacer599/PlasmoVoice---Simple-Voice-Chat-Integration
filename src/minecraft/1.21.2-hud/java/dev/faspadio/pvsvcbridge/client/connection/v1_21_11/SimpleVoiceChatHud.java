package dev.faspadio.pvsvcbridge.client.connection.v1_21_11;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public final class SimpleVoiceChatHud {

    private static final Identifier MICROPHONE_ICON =
            BridgeIdentifiers.of("plasmovoice", "textures/icons/microphone.png");
    private static final Identifier MICROPHONE_MUTED_ICON =
            BridgeIdentifiers.of("plasmovoice", "textures/icons/microphone_muted.png");
    private static final Identifier SPEAKER_MUTED_ICON =
            BridgeIdentifiers.of("plasmovoice", "textures/icons/speaker_muted.png");

    private static boolean registered;

    private SimpleVoiceChatHud() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register((context, tickDelta) -> render(context));
    }

    private static void render(DrawContext context) {
        SimpleVoiceChatClientBridge.HudState state = SimpleVoiceChatClientBridge.hudState();
        if (!state.connected() || (!state.transmitting() && !state.microphoneMuted() && !state.playbackMuted())) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int x = client.getWindow().getScaledWidth() / 2 - 8;
        int y = client.getWindow().getScaledHeight() - 52;
        Identifier icon = state.playbackMuted()
                ? SPEAKER_MUTED_ICON
                : state.transmitting() ? MICROPHONE_ICON : MICROPHONE_MUTED_ICON;
        if (client.getResourceManager().getResource(icon).isPresent()) {
            context.drawTexture(RenderLayer::getGuiTextured, icon, x, y, 0, 0, 16, 16, 16, 16);
        } else if (state.transmitting()) {
            drawFallbackTransmitIndicator(context, x, y);
        }
    }

    private static void drawFallbackTransmitIndicator(DrawContext context, int x, int y) {
        int shadow = 0xA0000000;
        int microphone = 0xFFF4F4F4;
        int accent = 0xFFBEBEBE;
        context.fill(x + 7, y + 2, x + 10, y + 9, shadow);
        context.fill(x + 5, y + 5, x + 7, y + 10, shadow);
        context.fill(x + 10, y + 5, x + 12, y + 10, shadow);
        context.fill(x + 7, y + 10, x + 10, y + 14, shadow);
        context.fill(x + 5, y + 14, x + 12, y + 15, shadow);
        context.fill(x + 6, y + 1, x + 9, y + 8, microphone);
        context.fill(x + 4, y + 4, x + 6, y + 9, microphone);
        context.fill(x + 9, y + 4, x + 11, y + 9, microphone);
        context.fill(x + 6, y + 8, x + 10, y + 10, microphone);
        context.fill(x + 7, y + 10, x + 9, y + 13, accent);
        context.fill(x + 5, y + 13, x + 11, y + 14, accent);
    }
}
