package dev.faspadio.pvsvcbridge.client.connection;

import dev.faspadio.pvsvcbridge.PvSvcBridge;
import dev.faspadio.pvsvcbridge.client.BridgeChat;
import dev.faspadio.pvsvcbridge.common.config.BridgeConfig;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import dev.faspadio.pvsvcbridge.common.text.BridgeTexts;
import dev.faspadio.pvsvcbridge.compat.detect.BackendDecision;
import dev.faspadio.pvsvcbridge.compat.detect.InstalledMods;
import dev.faspadio.pvsvcbridge.compat.detect.ServerVoiceSupport;
import dev.faspadio.pvsvcbridge.compat.detect.VoiceBackend;
import net.minecraft.util.Formatting;

public final class BridgeSession {

    private static final BridgeSession INSTANCE = new BridgeSession();

    private static final int PROBE_INTERVAL_TICKS = 5;
    private static final int MAX_PROBE_ATTEMPTS = 6;

    private boolean connected;
    private boolean resolved;
    private int ticks;
    private int attempts;
    private ServerVoiceSupport support = ServerVoiceSupport.UNKNOWN;
    private BackendDecision decision = new BackendDecision(VoiceBackend.NONE, false, "not connected");
    private BridgeState state = BridgeState.OFFLINE;

    private BridgeSession() {
    }

    public static BridgeSession get() {
        return INSTANCE;
    }

    public BridgeState state() {
        return state;
    }

    public ServerVoiceSupport support() {
        return support;
    }

    public BackendDecision decision() {
        return decision;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void onConnect() {
        SimpleVoiceChatClientBridge.stop();
        connected = true;
        resolved = false;
        ticks = 0;
        attempts = 0;
        support = ServerVoiceSupport.UNKNOWN;
        decision = new BackendDecision(VoiceBackend.NONE, false, "probing");
        state = BridgeState.PROBING;
        BridgeLog.info("Joined a server, probing voice backends");
    }

    public void onDisconnect() {
        SimpleVoiceChatClientBridge.stop();
        if (connected) {
            BridgeLog.info("Left the server, bridge state cleared");
        }
        connected = false;
        resolved = false;
        ticks = 0;
        attempts = 0;
        support = ServerVoiceSupport.UNKNOWN;
        decision = new BackendDecision(VoiceBackend.NONE, false, "not connected");
        state = BridgeState.OFFLINE;
    }

    public void tick() {
        if (!connected || resolved) {
            return;
        }
        if (ticks++ % PROBE_INTERVAL_TICKS != 0) {
            return;
        }

        probeNow("server join");
    }

    public void reevaluate(String cause) {
        if (!connected) {
            BridgeLog.debug("Reevaluation skipped, no server connection ({})", cause);
            return;
        }
        if (!resolved) {
            probeNow(cause);
            return;
        }
        resolve(cause);
    }

    public void probeForMenu() {
        if (connected && !resolved) {
            probeNow("voice menu opened");
        }
    }

    private boolean probeNow(String cause) {
        attempts++;
        ServerVoiceSupport probed;
        try {
            probed = ServerVoiceProbe.probe(PvSvcBridge.config());
        } catch (RuntimeException error) {
            BridgeLog.error("Voice backend probe failed", error);
            resolved = true;
            state = BridgeState.UNAVAILABLE;
            return true;
        }

        if (!probed.any() && attempts < MAX_PROBE_ATTEMPTS) {
            BridgeLog.debug("Probe {} of {} found no voice channels yet", attempts, MAX_PROBE_ATTEMPTS);
            return false;
        }

        resolved = true;
        support = probed;
        ServerVoiceProbe.logServerChannels();
        resolve(cause);
        return true;
    }

    private void resolve(String cause) {
        BridgeConfig config = PvSvcBridge.config();

        if (support.plasmoVoice()) {
            BridgeLog.info("Plasmo Voice detected");
        }
        if (support.simpleVoiceChat()) {
            BridgeLog.info("Simple Voice Chat detected");
        }

        decision = BackendDecision.decide(support, config);
        BridgeLog.info("Server voice backend: {}", decision.backend().displayName());
        BridgeLog.debug("Decision cause: {}, reason: {}", cause, decision.reason());

        state = apply(config);

        if (config.announceInChat()) {
            BridgeChat.send(BridgeTexts.translatable(state.translationKey()).formatted(Formatting.WHITE));
        }
    }

    private BridgeState apply(BridgeConfig config) {
        if (decision.backend() != VoiceBackend.SIMPLE_VOICE_CHAT) {
            SimpleVoiceChatClientBridge.stop();
        }
        switch (decision.backend()) {
            case PLASMO_VOICE -> {
                if (!InstalledMods.plasmoVoice()) {
                    BridgeLog.warn("Server runs Plasmo Voice but the client does not have it installed");
                    return BridgeState.UNAVAILABLE;
                }
                BridgeLog.info("Plasmo Voice keeps full control, bridge stays idle");
                return BridgeState.PLASMO_ACTIVE;
            }
            case SIMPLE_VOICE_CHAT -> {
                if (InstalledMods.simpleVoiceChat()) {
                    SimpleVoiceChatClientBridge.stop();
                    BridgeLog.info("Simple Voice Chat client is installed, it owns the voice connection, bridge stays idle");
                    return BridgeState.SIMPLE_VOICE_CHAT_NATIVE;
                }
                if (!config.bridgeEnabled()) {
                    BridgeLog.warn("Bridge is disabled in the config, no voice backend will be used");
                    return BridgeState.UNAVAILABLE;
                }
                if (!SimpleVoiceChatClientBridge.available()) {
                    BridgeLog.warn("Simple Voice Chat bridge transport is not available for this Minecraft version");
                    return BridgeState.SIMPLE_VOICE_CHAT_UNAVAILABLE;
                }
                SimpleVoiceChatClientBridge.start();
                BridgeLog.info("Using the Simple Voice Chat compatibility bridge");
                return BridgeState.SIMPLE_VOICE_CHAT_BRIDGE;
            }
            default -> {
                BridgeLog.warn("No voice chat system was detected on this server");
                return BridgeState.UNAVAILABLE;
            }
        }
    }
}
