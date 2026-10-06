package dev.faspadio.pvsvcbridge.client.connection.v1_21_11;

import net.minecraft.util.Identifier;

public final class BridgeIdentifiers {

    private BridgeIdentifiers() {
    }

    public static Identifier of(String namespace, String path) {
        return Identifier.of(namespace, path);
    }
}
