package dev.faspadio.pvsvcbridge.client;

import dev.faspadio.pvsvcbridge.PvSvcBridge;
import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.common.config.BackendPreference;
import dev.faspadio.pvsvcbridge.common.config.BridgeConfig;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import net.minecraft.client.MinecraftClient;
import su.plo.config.entry.BooleanConfigEntry;
import su.plo.config.entry.DoubleConfigEntry;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.device.AlContextOutputDevice;
import su.plo.voice.api.client.audio.device.DeviceException;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.client.config.addon.AddonConfig;

import javax.sound.sampled.AudioFormat;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlasmoVoiceSettings {

    private static BooleanConfigEntry preferSimpleVoiceChat;
    private static BooleanConfigEntry bridgeEnabled;
    private static BooleanConfigEntry fallback;
    private static BooleanConfigEntry allowDual;
    private static BooleanConfigEntry announce;
    private static BooleanConfigEntry debug;
    private static DoubleConfigEntry svcVolume;
    private static PlasmoVoiceClient voiceClient;
    private static double previousSvcVolume;
    private static boolean previousPreferSimpleVoiceChat;
    private static boolean previousBridgeEnabled;
    private static boolean previousFallback;
    private static boolean previousAllowDual;
    private static boolean previousAnnounce;
    private static boolean previousDebug;
    private static volatile boolean microphoneMuted;
    private static volatile boolean playbackMuted;
    private static volatile double playbackVolume = 1.0;
    private static volatile boolean microphoneTestActive;
    private static volatile boolean inputDeviceRefreshPending;
    private static volatile boolean outputDeviceRefreshPending;
    private static boolean bridgeActivationRestoreFailed;
    private static final Set<UUID> knownSpeakers = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Double> nativePlayerVolumes = new ConcurrentHashMap<>();
    private static final Set<UUID> nativeMutedPlayers = ConcurrentHashMap.newKeySet();

    private PlasmoVoiceSettings() {
    }

    public static void register(PlasmoVoiceClient voiceClient, AddonConfig addonConfig) {
        PlasmoVoiceSettings.voiceClient = voiceClient;
        BridgeConfig config = PvSvcBridge.config();
        preferSimpleVoiceChat = addonConfig.addToggle(
                "prefer-simple-backend",
                McTextComponent.translatable("pv-svc-bridge.settings.priority"),
                McTextComponent.translatable("pv-svc-bridge.settings.priority.tooltip"),
                config.priority() == BackendPreference.SIMPLE_VOICE_CHAT
        );
        bridgeEnabled = addonConfig.addToggle(
                "bridge-enabled",
                McTextComponent.translatable("pv-svc-bridge.settings.bridge_enabled"),
                McTextComponent.translatable("pv-svc-bridge.settings.bridge_enabled.tooltip"),
                config.bridgeEnabled()
        );
        fallback = addonConfig.addToggle(
                "simple-fallback",
                McTextComponent.translatable("pv-svc-bridge.settings.fallback"),
                McTextComponent.translatable("pv-svc-bridge.settings.fallback.tooltip"),
                config.fallbackToSimpleVoiceChat()
        );
        allowDual = addonConfig.addToggle(
                "allow-dual",
                McTextComponent.translatable("pv-svc-bridge.settings.allow_dual"),
                McTextComponent.translatable("pv-svc-bridge.settings.allow_dual.tooltip"),
                config.allowDualBackend()
        );
        announce = addonConfig.addToggle(
                "announce-backend",
                McTextComponent.translatable("pv-svc-bridge.settings.announce"),
                McTextComponent.translatable("pv-svc-bridge.settings.announce.tooltip"),
                config.announceInChat()
        );
        debug = addonConfig.addToggle(
                "debug-logging",
                McTextComponent.translatable("pv-svc-bridge.settings.debug"),
                McTextComponent.translatable("pv-svc-bridge.settings.debug.tooltip"),
                config.debug()
        );
        svcVolume = addonConfig.addVolumeSlider(
                "svc-volume",
                McTextComponent.translatable("pv-svc-bridge.settings.svc_volume"),
                McTextComponent.translatable("pv-svc-bridge.settings.svc_volume.tooltip"),
                "x",
                config.simpleVoiceChatVolume(),
                0,
                2
        );
        previousSvcVolume = svcVolume.value();
        previousPreferSimpleVoiceChat = preferSimpleVoiceChat.value();
        previousBridgeEnabled = bridgeEnabled.value();
        previousFallback = fallback.value();
        previousAllowDual = allowDual.value();
        previousAnnounce = announce.value();
        previousDebug = debug.value();
        applyWidgetValues(config);
    }

    public static void ensureBridgeActivation() {
        PlasmoVoiceClient client = voiceClient;
        if (client == null) {
            return;
        }
        var manager = client.getActivationManager();
        if (manager.getParentActivation().isPresent()) {
            bridgeActivationRestoreFailed = false;
            return;
        }
        try {
            Method fallbackMethod = manager.getClass().getDeclaredMethod("useFallbackParent");
            if (!fallbackMethod.trySetAccessible()) {
                throw new IllegalAccessException("Plasmo Voice fallback activation method is not accessible");
            }
            fallbackMethod.invoke(manager);
            if (manager.getParentActivation().isEmpty()) {
                throw new IllegalStateException("Plasmo Voice did not create its fallback activation");
            }
            bridgeActivationRestoreFailed = false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Throwable cause = error instanceof InvocationTargetException invocation
                    ? invocation.getTargetException()
                    : error;
            if (!bridgeActivationRestoreFailed) {
                BridgeLog.error("Could not initialize Plasmo Voice activation controls for the SVC bridge", cause);
                bridgeActivationRestoreFailed = true;
            }
        }
    }

    public static boolean isStereoCaptureSupported() {
        PlasmoVoiceClient client = voiceClient;
        return client != null
                && client.getConfig().getVoice().getStereoCapture().value()
                && !client.getConfig().getAdvanced().getStereoSourcesToMono().value();
    }

    public static void tick() {
        syncNativeVoiceSettings();
        if (preferSimpleVoiceChat == null) {
            return;
        }

        BridgeConfig config = PvSvcBridge.config();
        boolean changed = false;
        boolean backendChanged = false;
        double selectedSvcVolume = svcVolume.value();
        if (Double.compare(selectedSvcVolume, previousSvcVolume) != 0) {
            config.setSimpleVoiceChatVolume(selectedSvcVolume);
            changed = true;
        } else if (Double.compare(config.simpleVoiceChatVolume(), previousSvcVolume) != 0) {
            svcVolume.set(config.simpleVoiceChatVolume());
        }
        previousSvcVolume = svcVolume.value();

        boolean selectedPreferSimpleVoiceChat = preferSimpleVoiceChat.value();
        if (selectedPreferSimpleVoiceChat != previousPreferSimpleVoiceChat) {
            config.setPriority(selectedPreferSimpleVoiceChat
                    ? BackendPreference.SIMPLE_VOICE_CHAT
                    : BackendPreference.PLASMO);
            changed = true;
            backendChanged = true;
        } else if ((config.priority() == BackendPreference.SIMPLE_VOICE_CHAT) != previousPreferSimpleVoiceChat) {
            preferSimpleVoiceChat.set(config.priority() == BackendPreference.SIMPLE_VOICE_CHAT);
        }
        previousPreferSimpleVoiceChat = preferSimpleVoiceChat.value();

        boolean selectedBridgeEnabled = bridgeEnabled.value();
        if (selectedBridgeEnabled != previousBridgeEnabled) {
            config.setBridgeEnabled(selectedBridgeEnabled);
            changed = true;
            backendChanged = true;
        } else if (config.bridgeEnabled() != previousBridgeEnabled) {
            bridgeEnabled.set(config.bridgeEnabled());
        }
        previousBridgeEnabled = bridgeEnabled.value();

        boolean selectedFallback = fallback.value();
        if (selectedFallback != previousFallback) {
            config.setFallbackToSimpleVoiceChat(selectedFallback);
            changed = true;
            backendChanged = true;
        } else if (config.fallbackToSimpleVoiceChat() != previousFallback) {
            fallback.set(config.fallbackToSimpleVoiceChat());
        }
        previousFallback = fallback.value();

        boolean selectedAllowDual = allowDual.value();
        if (selectedAllowDual != previousAllowDual) {
            config.setAllowDualBackend(selectedAllowDual);
            changed = true;
            backendChanged = true;
        } else if (config.allowDualBackend() != previousAllowDual) {
            allowDual.set(config.allowDualBackend());
        }
        previousAllowDual = allowDual.value();

        boolean selectedAnnounce = announce.value();
        if (selectedAnnounce != previousAnnounce) {
            config.setAnnounceInChat(selectedAnnounce);
            changed = true;
        } else if (config.announceInChat() != previousAnnounce) {
            announce.set(config.announceInChat());
        }
        previousAnnounce = announce.value();

        boolean selectedDebug = debug.value();
        if (selectedDebug != previousDebug) {
            config.setDebug(selectedDebug);
            BridgeLog.setDebug(selectedDebug);
            changed = true;
        } else if (config.debug() != previousDebug) {
            debug.set(config.debug());
        }
        previousDebug = debug.value();

        if (changed) {
            PvSvcBridge.configManager().save();
            if (backendChanged) {
                BridgeSession.get().reevaluate("Plasmo Voice settings");
            }
        }
    }

    private static void applyWidgetValues(BridgeConfig config) {
        config.setPriority(previousPreferSimpleVoiceChat
                ? BackendPreference.SIMPLE_VOICE_CHAT
                : BackendPreference.PLASMO);
        config.setBridgeEnabled(previousBridgeEnabled);
        config.setFallbackToSimpleVoiceChat(previousFallback);
        config.setAllowDualBackend(previousAllowDual);
        config.setAnnounceInChat(previousAnnounce);
        config.setDebug(previousDebug);
        config.setSimpleVoiceChatVolume(previousSvcVolume);
        BridgeLog.setDebug(previousDebug);
        PvSvcBridge.configManager().save();
    }

    public static void noteSpeaker(UUID playerId) {
        knownSpeakers.add(playerId);
    }

    public static Set<UUID> knownSpeakersSnapshot() {
        return Set.copyOf(knownSpeakers);
    }

    public static PlasmoVoiceClient voiceClient() {
        return voiceClient;
    }

    public static boolean isMicrophoneMuted() {
        return microphoneMuted;
    }

    public static boolean isPlaybackMuted() {
        return playbackMuted;
    }

    public static boolean isMicrophoneTestActive() {
        return microphoneTestActive;
    }

    public static void setMicrophoneTestActive(boolean active) {
        microphoneTestActive = active;
    }

    public static void requestInputDeviceRefresh() {
        inputDeviceRefreshPending = true;
    }

    public static boolean consumeInputDeviceRefreshRequest() {
        boolean pending = inputDeviceRefreshPending;
        inputDeviceRefreshPending = false;
        return pending;
    }

    public static void requestOutputDeviceRefresh() {
        outputDeviceRefreshPending = true;
    }

    public static boolean consumeOutputDeviceRefreshRequest() {
        boolean pending = outputDeviceRefreshPending;
        outputDeviceRefreshPending = false;
        return pending;
    }

    public static double microphoneVolume() {
        PlasmoVoiceClient client = voiceClient;
        return client == null ? 1.0 : client.getConfig().getVoice().getMicrophoneVolume().value();
    }

    public static InputDevice openInputDevice(AudioFormat format) throws DeviceException {
        PlasmoVoiceClient client = voiceClient;
        if (client == null) {
            return null;
        }
        var voice = client.getConfig().getVoice();
        String factoryId = voice.getUseJavaxInput().value() ? "JAVAX_INPUT" : "AL_INPUT";
        var factory = client.getDeviceFactoryManager().getDeviceFactory(factoryId)
                .orElseThrow(() -> new DeviceException("Plasmo Voice input device factory is unavailable"));
        String selectedName = voice.getInputDevice().value();
        if (selectedName == null || selectedName.isBlank() || !factory.getDeviceNames().contains(selectedName)) {
            selectedName = factory.getDefaultDeviceName();
        }
        var device = factory.openDevice(format, selectedName);
        if (device instanceof InputDevice inputDevice) {
            registerInputDevice(client, inputDevice);
            return inputDevice;
        }
        device.close();
        throw new DeviceException("Plasmo Voice input factory returned a non-input device");
    }

    public static AlContextOutputDevice openOutputDevice(AudioFormat format) throws DeviceException {
        PlasmoVoiceClient client = voiceClient;
        if (client == null) {
            return null;
        }
        var factory = client.getDeviceFactoryManager().getDeviceFactory("AL_OUTPUT")
                .orElseThrow(() -> new DeviceException("Plasmo Voice output device factory is unavailable"));
        String selectedName = client.getConfig().getVoice().getOutputDevice().value();
        if (selectedName == null || selectedName.isBlank() || !factory.getDeviceNames().contains(selectedName)) {
            selectedName = factory.getDefaultDeviceName();
        }
        var device = factory.openDevice(format, selectedName);
        if (device instanceof AlContextOutputDevice outputDevice) {
            client.getDeviceManager().setOutputDevice(outputDevice);
            return outputDevice;
        }
        device.close();
        throw new DeviceException("Plasmo Voice output factory returned a non-output device");
    }

    public static void ensureOutputDevice(AudioFormat format) throws DeviceException {
        PlasmoVoiceClient client = voiceClient;
        if (client != null && client.getDeviceManager().getOutputDevice().isEmpty()) {
            openOutputDevice(format);
        }
    }

    public static void closeOutputDevice(AlContextOutputDevice outputDevice) {
        PlasmoVoiceClient client = voiceClient;
        if (client != null && client.getDeviceManager().getOutputDevice().orElse(null) == outputDevice) {
            client.getDeviceManager().setOutputDevice(null);
        }
        outputDevice.close();
    }

    private static void registerInputDevice(PlasmoVoiceClient client, InputDevice inputDevice) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null || minecraft.isOnThread()) {
            client.getDeviceManager().setInputDevice(inputDevice);
            return;
        }
        minecraft.execute(() -> client.getDeviceManager().setInputDevice(inputDevice));
    }

    public static void closeInputDevice(InputDevice inputDevice) {
        PlasmoVoiceClient client = voiceClient;
        if (client != null && client.getDeviceManager().getInputDevice().orElse(null) == inputDevice) {
            MinecraftClient minecraft = MinecraftClient.getInstance();
            if (minecraft == null || minecraft.isOnThread()) {
                client.getDeviceManager().setInputDevice(null);
            } else {
                minecraft.execute(() -> client.getDeviceManager().setInputDevice(null));
            }
        }
        try {
            inputDevice.stop();
        } catch (RuntimeException ignored) {
            // The audio backend already stopped this device.
        }
        inputDevice.close();
    }

    public static boolean isInputCaptureDisabled() {
        PlasmoVoiceClient client = voiceClient;
        if (client == null) {
            return false;
        }
        Object voice = client.getConfig().getVoice();
        try {
            Object entry = voice.getClass().getMethod("getDisableInputDevice").invoke(voice);
            Object value = entry.getClass().getMethod("value").invoke(entry);
            return value instanceof Boolean disabled && disabled;
        } catch (ReflectiveOperationException | RuntimeException error) {
            BridgeLog.debug("Could not read the Plasmo Voice input device setting", error);
            return false;
        }
    }

    public static double playbackVolume() {
        return playbackVolume;
    }

    public static double playerVolume(UUID playerId) {
        return nativePlayerVolumes.getOrDefault(
                playerId,
                PvSvcBridge.config().simpleVoiceChatPlayerVolume(playerId)
        );
    }

    public static boolean isPlayerMuted(UUID playerId) {
        return nativeMutedPlayers.contains(playerId);
    }

    public static double adjustPlayerVolume(UUID playerId, double delta) {
        double volume = normalizePlayerVolume(playerVolume(playerId) + delta);
        setPlayerVolume(playerId, volume);
        PvSvcBridge.configManager().save();
        return volume;
    }

    private static void setPlayerVolume(UUID playerId, double volume) {
        volume = normalizePlayerVolume(volume);
        PvSvcBridge.config().setSimpleVoiceChatPlayerVolume(playerId, volume);
        nativePlayerVolumes.put(playerId, volume);
        setPlayerMuted(playerId, volume == 0.0);
        PlasmoVoiceClient client = voiceClient;
        if (client != null) {
            client.getConfig().getVoice().getVolumes().getPlayerVolume(playerId).set(volume);
        }
    }

    private static double normalizePlayerVolume(double volume) {
        double clamped = Math.max(0.0, Math.min(2.0, volume));
        return clamped < 0.025 ? 0.0 : Math.max(0.05, Math.round(clamped * 20.0) / 20.0);
    }

    private static void setPlayerMuted(UUID playerId, boolean muted) {
        if (muted) {
            nativeMutedPlayers.add(playerId);
        } else {
            nativeMutedPlayers.remove(playerId);
        }
        PlasmoVoiceClient client = voiceClient;
        if (client != null) {
            client.getConfig().getVoice().getVolumes().getPlayerMute(playerId).set(muted);
        }
    }

    private static void syncNativeVoiceSettings() {
        PlasmoVoiceClient client = voiceClient;
        if (client == null) {
            microphoneMuted = false;
            playbackMuted = false;
            playbackVolume = 1.0;
            return;
        }
        var voice = client.getConfig().getVoice();
        microphoneMuted = voice.getMicrophoneDisabled().value();
        playbackMuted = voice.getDisabled().value();
        playbackVolume = voice.getVolume().value();
        var volumes = voice.getVolumes();
        for (UUID playerId : knownSpeakers) {
            nativeMutedPlayers.remove(playerId);
            if (volumes.getPlayerMute(playerId).value()) {
                nativeMutedPlayers.add(playerId);
            }
            double volume = volumes.getPlayerVolume(playerId).value();
            Double previousVolume = nativePlayerVolumes.put(playerId, volume);
            PvSvcBridge.config().setSimpleVoiceChatPlayerVolume(playerId, volume);
            if (volume <= 0.0) {
                setPlayerMuted(playerId, true);
            } else if (previousVolume != null && previousVolume <= 0.0 && volume >= 0.05) {
                setPlayerMuted(playerId, false);
            }
        }
    }

    public static void clearSpeakerWidgets() {
        knownSpeakers.clear();
        nativePlayerVolumes.clear();
        nativeMutedPlayers.clear();
    }

}
