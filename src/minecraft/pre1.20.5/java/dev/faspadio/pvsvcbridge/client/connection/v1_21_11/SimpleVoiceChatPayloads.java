package dev.faspadio.pvsvcbridge.client.connection.v1_21_11;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class SimpleVoiceChatPayloads {

    public static final Identifier REQUEST_SECRET_ID = BridgeIdentifiers.of("voicechat", "request_secret");
    public static final Identifier SECRET_ID = BridgeIdentifiers.of("voicechat", "secret");
    public static final Identifier UPDATE_STATE_ID = BridgeIdentifiers.of("voicechat", "update_state");

    private SimpleVoiceChatPayloads() {
    }

    public record SecretResponse(
            byte[] secret,
            int serverPort,
            UUID playerId,
            int codec,
            int mtuSize,
            double voiceDistance,
            int keepAliveMs,
            boolean groupsEnabled,
            String voiceHost,
            boolean recordingAllowed
    ) {
        public static SecretResponse read(PacketByteBuf buf) {
            byte[] secret = new byte[16];
            buf.readBytes(secret);
            return new SecretResponse(
                    secret,
                    buf.readInt(),
                    buf.readUuid(),
                    buf.readByte() & 0xFF,
                    buf.readInt(),
                    buf.readDouble(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readString(32767),
                    buf.readBoolean()
            );
        }
    }
}
