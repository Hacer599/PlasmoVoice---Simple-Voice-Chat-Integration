package dev.faspadio.pvsvcbridge.mixin;

import dev.faspadio.pvsvcbridge.client.PlasmoVoiceSettings;
import dev.faspadio.pvsvcbridge.client.connection.BridgeSession;
import dev.faspadio.pvsvcbridge.client.connection.BridgeState;
import dev.faspadio.pvsvcbridge.common.text.BridgeTexts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class SVCPlayerVolumeScrollMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void pvsvcbridge$adjustPlayerVolume(long window, double horizontal, double vertical, CallbackInfo callback) {
        if (vertical == 0
                || BridgeSession.get().state() != BridgeState.SIMPLE_VOICE_CHAT_BRIDGE
                || !isPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)
                || !isPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)
                || GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null) {
            return;
        }
        PlayerEntity player = findAimedPlayer(client);
        if (player == null) {
            return;
        }

        double volume = PlasmoVoiceSettings.adjustPlayerVolume(player.getUuid(), Math.signum(vertical) * 0.05);
        client.inGameHud.setOverlayMessage(BridgeTexts.translatable(
                "pv-svc-bridge.settings.player_volume.changed",
                player.getName(),
                Math.round(volume * 100)
        ), false);
        callback.cancel();
    }

    private static PlayerEntity findAimedPlayer(MinecraftClient client) {
        Vec3d start = client.player.getCameraPosVec(1.0F);
        Vec3d end = start.add(client.player.getRotationVec(1.0F).multiply(128.0));
        BlockHitResult blockHit = client.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        double nearestDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY
                : start.squaredDistanceTo(blockHit.getPos());
        PlayerEntity nearestPlayer = null;
        for (PlayerEntity candidate : client.world.getPlayers()) {
            if (candidate == client.player) {
                continue;
            }
            Box bounds = candidate.getBoundingBox().expand(0.05);
            var intersection = bounds.raycast(start, end);
            if (intersection.isEmpty()) {
                continue;
            }
            double distance = start.squaredDistanceTo(intersection.get());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = candidate;
            }
        }
        return nearestPlayer;
    }

    private static boolean isPressed(long window, int leftKey, int rightKey) {
        return GLFW.glfwGetKey(window, leftKey) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, rightKey) == GLFW.GLFW_PRESS;
    }
}
