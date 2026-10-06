package dev.faspadio.pvsvcbridge;

import dev.faspadio.pvsvcbridge.common.config.BridgeConfig;
import dev.faspadio.pvsvcbridge.common.config.BridgeConfigManager;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import dev.faspadio.pvsvcbridge.compat.detect.InstalledMods;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class PvSvcBridge implements ModInitializer {

    public static final String MOD_ID = "pv-svc-bridge";

    private static BridgeConfigManager configManager;

    @Override
    public void onInitialize() {
        configManager = new BridgeConfigManager(
                FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json")
        );
        configManager.load();

        BridgeLog.info("Version {} starting", InstalledMods.version(MOD_ID).orElse("unknown"));
        InstalledMods.logInstalled();
    }

    public static BridgeConfigManager configManager() {
        if (configManager == null) {
            configManager = new BridgeConfigManager(
                    FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json")
            );
            configManager.load();
        }
        return configManager;
    }

    public static BridgeConfig config() {
        return configManager().config();
    }
}
