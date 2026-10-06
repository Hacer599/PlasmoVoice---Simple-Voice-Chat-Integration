package dev.faspadio.pvsvcbridge.mixin;

import dev.faspadio.pvsvcbridge.client.PlasmoVoiceMenuHandler;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Pseudo
@Mixin(targets = "su.plo.voice.client.ModVoiceClient", remap = false)
public abstract class PlasmoVoiceMenuMixin {

    @Inject(method = "onKeyPressed", at = @At("HEAD"), cancellable = true, remap = false)
    private void pvsvcbridge$openSettingsDuringSvc(@Coerce Object event, CallbackInfo callback) {
        if (!isPlasmoMenuKeyPressed()) {
            return;
        }
        if (PlasmoVoiceMenuHandler.onMenuPressed()) {
            callback.cancel();
        }
    }

    private static boolean isPlasmoMenuKeyPressed() {
        try {
            Class<?> clientClass = Class.forName("su.plo.voice.client.ModVoiceClient");
            Field menuKeyField = clientClass.getField("MENU_KEY");
            Object menuKey = menuKeyField.get(null);
            return menuKey instanceof KeyBinding keyBinding && keyBinding.isPressed();
        } catch (ReflectiveOperationException | LinkageError error) {
            BridgeLog.error("Could not read the Plasmo Voice menu key binding", error);
            return false;
        }
    }

}
