package dev.faspadio.pvsvcbridge.client.connection.v1_21_11;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

public final class BridgeCamera {

    private BridgeCamera() {
    }

    public static Vec3d position(Camera camera) {
        return camera.getPos();
    }
}
