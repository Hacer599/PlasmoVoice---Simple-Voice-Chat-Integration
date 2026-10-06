package dev.faspadio.pvsvcbridge.client.command;

import com.mojang.brigadier.context.CommandContext;
import dev.faspadio.pvsvcbridge.PvSvcBridge;
import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.common.config.BackendPreference;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import dev.faspadio.pvsvcbridge.common.text.BridgeTexts;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class BridgeCommands {

    private BridgeCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommandManager.literal("pvbridge")
                        .executes(BridgeCommands::openSettings)
                        .then(ClientCommandManager.literal("settings")
                                .executes(BridgeCommands::openSettings))
                        .then(ClientCommandManager.literal("status")
                                .executes(BridgeCommands::reportStatus))
                        .then(ClientCommandManager.literal("priority")
                                .then(ClientCommandManager.literal(BackendPreference.PLASMO.id())
                                        .executes(context -> setPriority(context, BackendPreference.PLASMO)))
                                .then(ClientCommandManager.literal(BackendPreference.SIMPLE_VOICE_CHAT.id())
                                        .executes(context -> setPriority(context, BackendPreference.SIMPLE_VOICE_CHAT))))
        ));
    }

    private static int openSettings(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                Class<?> screenClass = Class.forName(
                        "dev.faspadio.pvsvcbridge.client.gui.BridgeSettingsScreen",
                        true,
                        BridgeCommands.class.getClassLoader()
                );
                Constructor<?> constructor = screenClass.getConstructor(Screen.class);
                Object instance = constructor.newInstance(new Object[]{null});
                if (!(instance instanceof Screen screen)) {
                    throw new IllegalStateException("Bridge settings screen is not a Minecraft screen");
                }
                client.setScreen(screen);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                Throwable cause = error instanceof InvocationTargetException invocation
                        ? invocation.getTargetException()
                        : error;
                BridgeLog.error("Could not open the bridge settings screen", cause);
                if (client.player != null) {
                    client.player.sendMessage(BridgeTexts.translatable("pv-svc-bridge.settings.open_failed"), false);
                }
            }
        });
        return 1;
    }

    private static int reportStatus(CommandContext<FabricClientCommandSource> context) {
        BridgeSession session = BridgeSession.get();
        context.getSource().sendFeedback(BridgeTexts.translatable(
                "pv-svc-bridge.command.status",
                BridgeTexts.translatable(session.state().translationKey()),
                session.decision().backend().displayName()
        ));
        return 1;
    }

    private static int setPriority(CommandContext<FabricClientCommandSource> context, BackendPreference preference) {
        PvSvcBridge.config().setPriority(preference);
        PvSvcBridge.configManager().save();
        BridgeSession.get().reevaluate("priority command");
        context.getSource().sendFeedback(BridgeTexts.translatable(
                "pv-svc-bridge.command.priority_set",
                BridgeTexts.translatable(preference.translationKey())
        ));
        return 1;
    }
}
