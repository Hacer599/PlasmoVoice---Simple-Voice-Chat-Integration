package dev.faspadio.pvsvcbridge.compat.detect;

import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InstalledMods {

    public static final String PLASMO_VOICE = "plasmovoice";
    public static final String SIMPLE_VOICE_CHAT = "voicechat";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");

    private InstalledMods() {
    }

    public static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static Optional<String> version(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString());
    }

    public static boolean plasmoVoice() {
        return isLoaded(PLASMO_VOICE);
    }

    public static boolean plasmoVoiceAddonApiAvailable() {
        Optional<String> version = version(PLASMO_VOICE);
        if (version.isEmpty()) {
            return false;
        }

        Matcher matcher = VERSION_PATTERN.matcher(version.get());
        if (!matcher.find()) {
            BridgeLog.warn("Could not parse Plasmo Voice version {}, add-on menu integration is disabled", version.get());
            return false;
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        return major > 2 || major == 2 && (minor > 1 || minor == 1 && patch >= 16);
    }

    public static boolean simpleVoiceChat() {
        return isLoaded(SIMPLE_VOICE_CHAT);
    }

    public static void logInstalled() {
        if (plasmoVoice()) {
            BridgeLog.info("Plasmo Voice installed, version {}", version(PLASMO_VOICE).orElse("unknown"));
        } else {
            BridgeLog.warn("Plasmo Voice is not installed, this addon has nothing to extend");
        }
        if (simpleVoiceChat()) {
            BridgeLog.info("Simple Voice Chat installed, version {}", version(SIMPLE_VOICE_CHAT).orElse("unknown"));
        } else {
            BridgeLog.debug("Simple Voice Chat is not installed on this client");
        }
    }
}
