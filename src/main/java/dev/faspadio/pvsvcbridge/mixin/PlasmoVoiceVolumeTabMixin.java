package dev.faspadio.pvsvcbridge.mixin;

import dev.faspadio.pvsvcbridge.client.PlasmoVoiceSettings;
import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.client.connection.BridgeState;
import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.slib.api.entity.player.McGameProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Pseudo
@Mixin(targets = "su.plo.voice.client.gui.settings.tab.VolumeTabWidget", remap = false)
public abstract class PlasmoVoiceVolumeTabMixin {

    @Shadow(remap = false)
    private void createPlayerVolume(McGameProfile profile) {
        throw new AssertionError();
    }

    @Inject(method = "refreshPlayerEntries", at = @At("TAIL"), remap = false)
    private void pvsvcbridge$appendSvcPlayers(CallbackInfo callback) {
        if (BridgeSession.get().state() != BridgeState.SIMPLE_VOICE_CHAT_BRIDGE) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null || client.player == null) {
            return;
        }
        UUID self = client.player.getUuid();
        List<McGameProfile> profiles = new ArrayList<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            var profile = entry.getProfile();
            if (profile == null) {
                continue;
            }
            UUID playerId = readProfileId(profile);
            if (playerId == null || playerId.equals(self)) {
                continue;
            }
            profiles.add(new McGameProfile(
                    playerId,
                    readPlayerName(profile, entry, playerId),
                    readSkinProperties(profile)
            ));
        }
        profiles.sort(Comparator.comparing(McGameProfile::getName, String.CASE_INSENSITIVE_ORDER));
        for (McGameProfile profile : profiles) {
            PlasmoVoiceSettings.noteSpeaker(profile.getId());
            createPlayerVolume(profile);
        }
    }

    private static UUID readProfileId(Object profile) {
        try {
            Object id = invokeProfileMethod(profile, "id", "getId");
            return id instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException error) {
            BridgeLog.debug("Could not read the player's profile id", error);
            return null;
        }
    }

    private static String readPlayerName(Object profile, PlayerListEntry entry, UUID playerId) {
        try {
            return (String) invokeProfileMethod(profile, "name", "getName");
        } catch (ReflectiveOperationException error) {
            BridgeLog.debug("Could not read the SVC speaker's profile name", error);
            return entry.getDisplayName() == null ? playerId.toString() : entry.getDisplayName().getString();
        }
    }

    private static List<McGameProfile.Property> readSkinProperties(Object profile) {
        try {
            Object propertyMap = invokeProfileMethod(profile, "properties", "getProperties");
            Object textures = propertyMap.getClass().getMethod("get", Object.class).invoke(propertyMap, "textures");
            if (!(textures instanceof Iterable<?> textureProperties)) {
                return List.of();
            }
            List<McGameProfile.Property> properties = new ArrayList<>();
            for (Object property : textureProperties) {
                String name = (String) invokeProfileMethod(property, "name", "getName");
                String value = (String) invokeProfileMethod(property, "value", "getValue");
                String signature = (String) invokeProfileMethod(property, "signature", "getSignature");
                properties.add(new McGameProfile.Property(name, value, signature));
            }
            return properties;
        } catch (ReflectiveOperationException error) {
            BridgeLog.debug("Could not read the SVC speaker's skin properties", error);
            return List.of();
        }
    }

    private static Object invokeProfileMethod(Object target, String currentName, String legacyName)
            throws ReflectiveOperationException {
        try {
            return target.getClass().getMethod(currentName).invoke(target);
        } catch (NoSuchMethodException missingCurrentName) {
            return target.getClass().getMethod(legacyName).invoke(target);
        }
    }
}
