package dev.faspadio.pvsvcbridge.client.gui;

import dev.faspadio.pvsvcbridge.PvSvcBridge;
import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.common.config.BridgeConfig;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class BridgeSettingsScreen extends Screen {

    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_STEP = 24;
    private static final int FIRST_ROW_Y = 52;

    private final Screen parent;

    public BridgeSettingsScreen(Screen parent) {
        super(Text.translatable("pv-svc-bridge.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - BUTTON_WIDTH / 2;
        int row = 0;

        addDrawableChild(ButtonWidget.builder(priorityLabel(), button -> {
            BridgeConfig config = PvSvcBridge.config();
            config.setPriority(config.priority().opposite());
            button.setMessage(priorityLabel());
            applyChange("priority switched to " + config.priority().id());
        }).dimensions(left, rowY(row++), BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addToggle(left, row++, "pv-svc-bridge.settings.bridge_enabled", "bridgeEnabled",
                () -> PvSvcBridge.config().bridgeEnabled(),
                value -> PvSvcBridge.config().setBridgeEnabled(value));

        addToggle(left, row++, "pv-svc-bridge.settings.fallback", "fallbackToSimpleVoiceChat",
                () -> PvSvcBridge.config().fallbackToSimpleVoiceChat(),
                value -> PvSvcBridge.config().setFallbackToSimpleVoiceChat(value));

        addToggle(left, row++, "pv-svc-bridge.settings.allow_dual", "allowDualBackend",
                () -> PvSvcBridge.config().allowDualBackend(),
                value -> PvSvcBridge.config().setAllowDualBackend(value));

        addToggle(left, row++, "pv-svc-bridge.settings.announce", "announceInChat",
                () -> PvSvcBridge.config().announceInChat(),
                value -> PvSvcBridge.config().setAnnounceInChat(value));

        addToggle(left, row++, "pv-svc-bridge.settings.debug", "debug",
                () -> PvSvcBridge.config().debug(),
                value -> {
                    PvSvcBridge.config().setDebug(value);
                    BridgeLog.setDebug(value);
                });

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close())
                .dimensions(left, this.height - 32, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void addToggle(int left, int row, String translationKey, String logName,
                           BooleanSupplier getter, Consumer<Boolean> setter) {
        addDrawableChild(ButtonWidget.builder(toggleLabel(translationKey, getter.getAsBoolean()), button -> {
            boolean next = !getter.getAsBoolean();
            setter.accept(next);
            button.setMessage(toggleLabel(translationKey, next));
            applyChange(logName + " set to " + next);
        }).dimensions(left, rowY(row), BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private static int rowY(int row) {
        return FIRST_ROW_Y + row * ROW_STEP;
    }

    private void applyChange(String description) {
        PvSvcBridge.configManager().save();
        BridgeLog.info("Settings changed: {}", description);
        BridgeSession.get().reevaluate("settings screen");
    }

    private static Text priorityLabel() {
        return Text.translatable("pv-svc-bridge.settings.priority")
                .append(": ")
                .append(Text.translatable(PvSvcBridge.config().priority().translationKey()));
    }

    private static Text toggleLabel(String translationKey, boolean value) {
        return Text.translatable(translationKey).append(": ").append(ScreenTexts.onOrOff(value));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        BridgeSession session = BridgeSession.get();

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 16, 0xFFFFFF);

        Text status = Text.translatable("pv-svc-bridge.settings.status")
                .append(": ")
                .append(Text.translatable(session.state().translationKey()).formatted(Formatting.YELLOW));
        context.drawCenteredTextWithShadow(this.textRenderer, status, centerX, 32, 0xA0A0A0);

        Text detected = Text.translatable("pv-svc-bridge.settings.detected")
                .append(": ")
                .append(detectedText(session));
        context.drawCenteredTextWithShadow(this.textRenderer, detected, centerX, this.height - 50, 0xA0A0A0);
    }

    private static Text detectedText(BridgeSession session) {
        boolean plasmoVoice = session.support().plasmoVoice();
        boolean simpleVoiceChat = session.support().simpleVoiceChat();
        if (plasmoVoice && simpleVoiceChat) {
            return Text.literal("Plasmo Voice + Simple Voice Chat");
        }
        if (plasmoVoice) {
            return Text.literal("Plasmo Voice");
        }
        if (simpleVoiceChat) {
            return Text.literal("Simple Voice Chat");
        }
        return Text.translatable("pv-svc-bridge.settings.detected_none");
    }

    @Override
    public void close() {
        PvSvcBridge.configManager().save();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
