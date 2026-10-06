package dev.faspadio.pvsvcbridge.client.connection.v1_21_11;

import dev.faspadio.pvsvcbridge.client.PlasmoVoiceSettings;
import dev.faspadio.pvsvcbridge.client.connection.SimpleVoiceChatTransport;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SimpleVoiceChatClientBridge {

    private static final int COMPATIBILITY_VERSION = 20;
    private static boolean initialized;
    private static boolean registrationFailed;
    private static boolean active;
    private static boolean requestSent;
    private static long nextRequestAt;
    private static long requestSentAt;
    private static long lastTickAt;
    private static SimpleVoiceChatTransport transport;

    private SimpleVoiceChatClientBridge() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (dev.faspadio.pvsvcbridge.compat.detect.InstalledMods.simpleVoiceChat()) {
            BridgeLog.info("Simple Voice Chat client is installed; its native connection has priority");
            registrationFailed = true;
            return;
        }
        if (!SimpleVoiceChatNetworking.initialize(SimpleVoiceChatClientBridge::acceptSecret)) {
            registrationFailed = true;
            return;
        }
        registerHud();
        BridgeLog.info("Simple Voice Chat compatibility transport registered; hold Left Alt to talk");
    }

    public static void start() {
        if (!initialized || registrationFailed) {
            return;
        }
        PlasmoVoiceSettings.ensureBridgeActivation();
        active = true;
        if (requestSent || System.currentTimeMillis() < nextRequestAt) {
            return;
        }
        if (!SimpleVoiceChatNetworking.canSendRequest()) {
            BridgeLog.warn("Simple Voice Chat server does not accept secret requests");
            nextRequestAt = System.currentTimeMillis() + 5000;
            return;
        }
        requestSent = true;
        requestSentAt = System.currentTimeMillis();
        try {
            SimpleVoiceChatNetworking.sendRequest(COMPATIBILITY_VERSION);
        } catch (IllegalStateException error) {
            requestSent = false;
            nextRequestAt = System.currentTimeMillis() + 5000;
            BridgeLog.error("Could not send the Simple Voice Chat credential request", error);
            return;
        }
        BridgeLog.info("Requested Simple Voice Chat connection credentials");
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (lastTickAt != 0 && now - lastTickAt > 2500 && transport != null && transport.isConnected()) {
            BridgeLog.info("Refreshing voice audio devices after the client resumed");
            transport.refreshAudioDevices();
        }
        lastTickAt = now;
        if (transport != null && !transport.isRunning()) {
            closeTransport();
            requestSent = false;
            nextRequestAt = now + 5000;
        }
        if (transport != null) {
            if (PlasmoVoiceSettings.consumeInputDeviceRefreshRequest()) {
                transport.refreshInputDevice();
            }
            if (PlasmoVoiceSettings.consumeOutputDeviceRefreshRequest()) {
                transport.refreshOutputDevice();
            }
            transport.setPushToTalk(isPushToTalkPressed() && !PlasmoVoiceSettings.isMicrophoneMuted());
            transport.setVoiceActivation(isVoiceActivationEnabled() && !PlasmoVoiceSettings.isMicrophoneMuted());
            transport.setMicrophoneTest(PlasmoVoiceSettings.isMicrophoneTestActive());
            updateSpatialSnapshot();
            transport.drainDiscoveredSpeakers().forEach(PlasmoVoiceSettings::noteSpeaker);
        }
        if (requestSent && transport == null && now - requestSentAt > 30_000) {
            requestSent = false;
            nextRequestAt = now + 5000;
            BridgeLog.warn("Timed out waiting for Simple Voice Chat connection credentials");
        }
        if (active && !requestSent && transport == null) {
            start();
        }
    }

    public static void stop() {
        active = false;
        requestSent = false;
        nextRequestAt = 0;
        PlasmoVoiceSettings.setMicrophoneTestActive(false);
        closeTransport();
    }

    private static boolean isPushToTalkPressed() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || client.getWindow() == null) {
            return false;
        }
        var voiceClient = PlasmoVoiceSettings.voiceClient();
        if (voiceClient != null) {
            var manager = voiceClient.getActivationManager();
            boolean hasPushToTalkActivation = false;
            var parent = manager.getParentActivation();
            if (parent.isPresent() && !parent.get().isDisabled()
                    && parent.get().getType() == ClientActivation.Type.PUSH_TO_TALK) {
                hasPushToTalkActivation = true;
                if (parent.get().getPttKey().isPressed()) {
                    return true;
                }
            }
            for (ClientActivation activation : manager.getActivations()) {
                if (activation.getType() == ClientActivation.Type.PUSH_TO_TALK && !activation.isDisabled()) {
                    hasPushToTalkActivation = true;
                    if (activation.getPttKey().isPressed()) {
                        return true;
                    }
                }
            }
            if (hasPushToTalkActivation) {
                return false;
            }
        }
        return GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
    }

    public static boolean isPushToTalkActive() {
        SimpleVoiceChatTransport current = transport;
        return current != null && current.isPushToTalkActive();
    }

    public static HudState hudState() {
        SimpleVoiceChatTransport current = transport;
        return new HudState(
                current != null && current.isConnected(),
                isPushToTalkActive(),
                PlasmoVoiceSettings.isMicrophoneMuted(),
                PlasmoVoiceSettings.isPlaybackMuted()
        );
    }

    public record HudState(boolean connected, boolean transmitting, boolean microphoneMuted, boolean playbackMuted) {
    }

    private static boolean isVoiceActivationEnabled() {
        var voiceClient = PlasmoVoiceSettings.voiceClient();
        if (voiceClient == null) {
            return false;
        }
        var parent = voiceClient.getActivationManager().getParentActivation();
        if (parent.isPresent() && !parent.get().isDisabled()
                && parent.get().getType() != ClientActivation.Type.PUSH_TO_TALK) {
            return true;
        }
        for (ClientActivation activation : voiceClient.getActivationManager().getActivations()) {
            if (!activation.isDisabled() && activation.getType() == ClientActivation.Type.VOICE) {
                return true;
            }
        }
        return false;
    }

    private static void updateSpatialSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || transport == null) {
            return;
        }
        Map<UUID, SimpleVoiceChatTransport.SpeakerPosition> speakers = new HashMap<>();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player != client.player) {
                speakers.put(
                        player.getUuid(),
                        new SimpleVoiceChatTransport.SpeakerPosition(player.getX(), player.getEyeY(), player.getZ())
                );
            }
        }
        var camera = client.gameRenderer.getCamera();
        var cameraPosition = BridgeCamera.position(camera);
        transport.updateSpatialSnapshot(new SimpleVoiceChatTransport.SpatialSnapshot(
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z,
                camera.getYaw(),
                speakers
        ));
    }

    private static void registerHud() {
        SimpleVoiceChatHud.register();
    }

    private static void acceptSecret(SimpleVoiceChatPayloads.SecretResponse payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active || !requestSent || registrationFailed || client.getNetworkHandler() == null) {
            return;
        }
        if (transport != null && transport.isRunning()) {
            return;
        }
        closeTransport();
        try {
            transport = SimpleVoiceChatTransport.connect(
                    payload,
                    client.getNetworkHandler().getConnection().getAddress()
            );
            SimpleVoiceChatNetworking.sendUpdateState(false);
            BridgeLog.info(
                    "Received Simple Voice Chat credentials for UDP port {} and compatibility version {}",
                    payload.serverPort(),
                    COMPATIBILITY_VERSION
            );
        } catch (IOException | IllegalStateException error) {
            closeTransport();
            requestSent = false;
            nextRequestAt = System.currentTimeMillis() + 5000;
            BridgeLog.error("Could not start the Simple Voice Chat UDP transport", error);
        }
    }

    private static void closeTransport() {
        if (transport != null) {
            transport.close();
            transport = null;
        }
    }
}
