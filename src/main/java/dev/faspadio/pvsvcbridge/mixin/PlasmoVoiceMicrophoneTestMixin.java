package dev.faspadio.pvsvcbridge.mixin;

import dev.faspadio.pvsvcbridge.client.PlasmoVoiceSettings;
import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.client.connection.BridgeState;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.voice.api.client.audio.source.LoopbackSource;
import su.plo.voice.api.event.Event;

import javax.sound.sampled.AudioFormat;

@Pseudo
@Mixin(targets = "su.plo.voice.client.gui.settings.MicrophoneTestController", remap = false)
public abstract class PlasmoVoiceMicrophoneTestMixin {

    @Shadow(remap = false)
    private LoopbackSource source;

    @Inject(method = "start", at = @At("HEAD"), cancellable = true, remap = false)
    private void pvsvcbridge$startBridgeMicrophoneTest(CallbackInfo callback) {
        if (BridgeSession.get().state() != BridgeState.SIMPLE_VOICE_CHAT_BRIDGE) {
            return;
        }
        callback.cancel();
        if (source != null) {
            PlasmoVoiceSettings.setMicrophoneTestActive(true);
            return;
        }
        var voiceClient = PlasmoVoiceSettings.voiceClient();
        if (voiceClient == null) {
            return;
        }
        try {
            PlasmoVoiceSettings.ensureOutputDevice(new AudioFormat(48_000, 16, 2, true, false));
            source = voiceClient.getSourceManager().createLoopbackSource(true);
            source.initialize(PlasmoVoiceSettings.isStereoCaptureSupported());
            Class<?> controllerClass = Class.forName("su.plo.voice.client.gui.settings.MicrophoneTestController");
            Class<?> eventClass = Class.forName("su.plo.voice.client.event.gui.MicrophoneTestStartedEvent");
            var constructor = eventClass.getDeclaredConstructor(controllerClass);
            constructor.setAccessible(true);
            Object event = constructor.newInstance(this);
            voiceClient.getEventBus().fire((Event) event);
            PlasmoVoiceSettings.setMicrophoneTestActive(true);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            if (source != null) {
                source.close();
                source = null;
            }
            PlasmoVoiceSettings.setMicrophoneTestActive(false);
            BridgeLog.error("Could not start the Plasmo Voice microphone test for the SVC bridge", error);
        } catch (su.plo.voice.api.client.audio.device.DeviceException error) {
            if (source != null) {
                source.close();
            }
            source = null;
            PlasmoVoiceSettings.setMicrophoneTestActive(false);
            BridgeLog.error("Could not open Plasmo Voice output for the SVC microphone test", error);
        }
    }

    @Inject(method = "stop", at = @At("TAIL"), remap = false)
    private void pvsvcbridge$stopBridgeMicrophoneTest(CallbackInfo callback) {
        PlasmoVoiceSettings.setMicrophoneTestActive(false);
    }
}
