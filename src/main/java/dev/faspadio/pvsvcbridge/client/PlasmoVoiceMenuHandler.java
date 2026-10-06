package dev.faspadio.pvsvcbridge.client;

import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.client.connection.BridgeState;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;

import java.lang.reflect.Method;
import java.util.Optional;

public final class PlasmoVoiceMenuHandler {

    private static boolean pendingSettingsOpen;

    private PlasmoVoiceMenuHandler() {
    }

    public static boolean onMenuPressed() {
        BridgeSession session = BridgeSession.get();
        if (session.isConnected() && !session.isResolved()) {
            session.probeForMenu();
            if (!session.isResolved()) {
                pendingSettingsOpen = true;
                return true;
            }
        }
        if (!session.support().simpleVoiceChat() || session.state() == BridgeState.PLASMO_ACTIVE) {
            return false;
        }
        try {
            openPlasmoSettings();
            return true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
            BridgeLog.error("Could not open the Plasmo Voice settings screen on a Simple Voice Chat server", error);
            return false;
        }
    }

    public static void tick() {
        if (!pendingSettingsOpen) {
            return;
        }
        BridgeSession session = BridgeSession.get();
        if (!session.isConnected()) {
            pendingSettingsOpen = false;
            return;
        }
        if (!session.isResolved()) {
            return;
        }
        pendingSettingsOpen = false;
        try {
            openPlasmoSettings();
        } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
            BridgeLog.error("Could not open Plasmo Voice settings after detecting the server backend", error);
        }
    }

    private static void openPlasmoSettings() throws ReflectiveOperationException {
        if (BridgeSession.get().state() == BridgeState.SIMPLE_VOICE_CHAT_BRIDGE) {
            PlasmoVoiceSettings.ensureBridgeActivation();
        }
        Class<?> clientClass = Class.forName("su.plo.voice.client.ModVoiceClient");
        Object voiceClient = clientClass.getField("INSTANCE").get(null);
        if (voiceClient == null) {
            throw new ReflectiveOperationException("Plasmo Voice client is not initialized");
        }
        Class<?> settingsClass = Class.forName("su.plo.voice.client.gui.settings.VoiceSettingsScreen");
        Class<?> wrapperClass = Class.forName("su.plo.lib.mod.client.gui.screen.ScreenWrapper");
        Method currentScreenMethod = wrapperClass.getMethod("getCurrentWrappedScreen");
        Optional<?> currentWrapper = (Optional<?>) currentScreenMethod.invoke(null);
        if (currentWrapper.isPresent()) {
            Object currentScreen = currentWrapper.get().getClass().getMethod("getScreen").invoke(currentWrapper.get());
            if (settingsClass.isInstance(currentScreen)) {
                Method closeSettings = wrapperClass.getMethod(
                        "openScreen",
                        Class.forName("su.plo.lib.mod.client.gui.screen.GuiScreen")
                );
                closeSettings.invoke(null, new Object[]{null});
                return;
            }
        }
        Object settingsScreen = null;
        for (var constructor : settingsClass.getConstructors()) {
            if (constructor.getParameterCount() == 1
                    && constructor.getParameterTypes()[0].isInstance(voiceClient)) {
                settingsScreen = constructor.newInstance(voiceClient);
                break;
            }
        }
        if (settingsScreen == null) {
            throw new NoSuchMethodException("Plasmo Voice settings screen constructor was not found");
        }
        Class<?> guiScreenClass = Class.forName("su.plo.lib.mod.client.gui.screen.GuiScreen");
        Method openScreen = wrapperClass.getMethod("openScreen", guiScreenClass);
        openScreen.invoke(null, settingsScreen);
    }
}
