package dev.faspadio.pvsvcbridge.client.connection.v1_21_11;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;

import java.io.IOException;

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
        HudRenderCallback.EVENT.register(SimpleVoiceChatHud::render);
    }

    private static void render(MatrixStack matrices, float tickDelta) {
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
        try {
            client.getResourceManager().getResource(icon);
            RenderSystem.setShaderTexture(0, icon);
            DrawableHelper.drawTexture(matrices, x, y, 0, 0, 16, 16, 16, 16);
        } catch (IOException missingIcon) {
            if (state.transmitting()) {
                drawFallbackTransmitIndicator(matrices, x, y);
            }
        }
    }

    private static void drawFallbackTransmitIndicator(MatrixStack matrices, int x, int y) {
        int shadow = 0xA0000000;
        int microphone = 0xFFF4F4F4;
        int accent = 0xFFBEBEBE;
        DrawableHelper.fill(matrices, x + 7, y + 2, x + 10, y + 9, shadow);
        DrawableHelper.fill(matrices, x + 5, y + 5, x + 7, y + 10, shadow);
        DrawableHelper.fill(matrices, x + 10, y + 5, x + 12, y + 10, shadow);
        DrawableHelper.fill(matrices, x + 7, y + 10, x + 10, y + 14, shadow);
        DrawableHelper.fill(matrices, x + 5, y + 14, x + 12, y + 15, shadow);
        DrawableHelper.fill(matrices, x + 6, y + 1, x + 9, y + 8, microphone);
        DrawableHelper.fill(matrices, x + 4, y + 4, x + 6, y + 9, microphone);
        DrawableHelper.fill(matrices, x + 9, y + 4, x + 11, y + 9, microphone);
        DrawableHelper.fill(matrices, x + 6, y + 8, x + 10, y + 10, microphone);
        DrawableHelper.fill(matrices, x + 7, y + 10, x + 9, y + 13, accent);
        DrawableHelper.fill(matrices, x + 5, y + 13, x + 11, y + 14, accent);
    }
}
