package dev.faspadio.pvsvcbridge.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class BridgeConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private BridgeConfig config = new BridgeConfig();

    public BridgeConfigManager(Path file) {
        this.file = file;
    }

    public BridgeConfig config() {
        return config;
    }

    public void load() {
        BridgeConfig loaded = readFile();
        if (loaded == null) {
            config = new BridgeConfig();
            BridgeLog.setDebug(config.debug());
            save();
            return;
        }
        loaded.normalize();
        config = loaded;
        BridgeLog.setDebug(config.debug());
        BridgeLog.info(
                "Config loaded: priority={}, bridgeEnabled={}, fallback={}, allowDualBackend={}",
                config.priority().id(),
                config.bridgeEnabled(),
                config.fallbackToSimpleVoiceChat(),
                config.allowDualBackend()
        );
    }

    private BridgeConfig readFile() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return GSON.fromJson(json, BridgeConfig.class);
        } catch (IOException error) {
            BridgeLog.error("Failed to read config, defaults will be used", error);
            return null;
        } catch (RuntimeException error) {
            BridgeLog.error("Config file is malformed, defaults will be used", error);
            return null;
        }
    }

    public void save() {
        config.normalize();
        BridgeLog.setDebug(config.debug());
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                BridgeLog.debug("Atomic config move unavailable, falling back to plain replace", atomicFailure);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            BridgeLog.error("Failed to save config", error);
        }
    }
}
