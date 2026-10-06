package dev.faspadio.pvsvcbridge.compat.detect;

import dev.faspadio.pvsvcbridge.common.config.BackendPreference;
import dev.faspadio.pvsvcbridge.common.config.BridgeConfig;

public record BackendDecision(VoiceBackend backend, boolean bridgeRequired, String reason) {

    public static BackendDecision decide(ServerVoiceSupport support, BridgeConfig config) {
        if (!support.any()) {
            return new BackendDecision(VoiceBackend.NONE, false, "server does not advertise any supported voice system");
        }

        if (support.both()) {
            if (config.allowDualBackend()) {
                BackendPreference preferred = config.priority();
                if (preferred == BackendPreference.SIMPLE_VOICE_CHAT && bridgeUsable(config)) {
                    return new BackendDecision(
                            VoiceBackend.SIMPLE_VOICE_CHAT,
                            true,
                            "both systems present, dual backend allowed, Simple Voice Chat requested"
                    );
                }
                return new BackendDecision(
                        VoiceBackend.PLASMO_VOICE,
                        false,
                        "both systems present, dual backend allowed, Plasmo Voice requested"
                );
            }
            if (config.priority() == BackendPreference.SIMPLE_VOICE_CHAT && bridgeUsable(config)) {
                return new BackendDecision(
                        VoiceBackend.SIMPLE_VOICE_CHAT,
                        true,
                        "both systems present, manual preference is Simple Voice Chat"
                );
            }
            return new BackendDecision(
                    VoiceBackend.PLASMO_VOICE,
                    false,
                    "both systems present, Plasmo Voice has priority, bridge stays idle"
            );
        }

        if (support.plasmoVoice()) {
            return new BackendDecision(VoiceBackend.PLASMO_VOICE, false, "server runs Plasmo Voice");
        }

        if (!config.bridgeEnabled()) {
            return new BackendDecision(VoiceBackend.NONE, false, "server runs Simple Voice Chat but the bridge is disabled");
        }
        if (!config.fallbackToSimpleVoiceChat()) {
            return new BackendDecision(VoiceBackend.NONE, false, "server runs Simple Voice Chat but fallback is disabled");
        }
        return new BackendDecision(VoiceBackend.SIMPLE_VOICE_CHAT, true, "server runs Simple Voice Chat only");
    }

    private static boolean bridgeUsable(BridgeConfig config) {
        return config.bridgeEnabled() && config.fallbackToSimpleVoiceChat();
    }
}
