package dev.faspadio.pvsvcbridge.mixin;

import dev.faspadio.pvsvcbridge.client.PlasmoVoiceSettings;
import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.client.connection.BridgeState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "su.plo.voice.client.gui.settings.tab.DevicesTabWidget", remap = false)
public abstract class PlasmoVoiceDevicesMixin {

    @Shadow(remap = false)
    public abstract void init();

    @Inject(method = "reloadInputDevice", at = @At("HEAD"), cancellable = true, remap = false)
    private void pvsvcbridge$reloadBridgeInputDevice(CallbackInfo callback) {
        if (BridgeSession.get().state() != BridgeState.SIMPLE_VOICE_CHAT_BRIDGE) {
            return;
        }
        PlasmoVoiceSettings.requestInputDeviceRefresh();
        refreshTab();
        callback.cancel();
    }

    @Inject(method = "reloadOutputDevice", at = @At("HEAD"), cancellable = true, remap = false)
    private void pvsvcbridge$refreshBridgeOutputDevice(CallbackInfo callback) {
        if (BridgeSession.get().state() != BridgeState.SIMPLE_VOICE_CHAT_BRIDGE) {
            return;
        }
        PlasmoVoiceSettings.requestOutputDeviceRefresh();
        refreshTab();
        callback.cancel();
    }

    private void refreshTab() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(this::init);
    }
}
