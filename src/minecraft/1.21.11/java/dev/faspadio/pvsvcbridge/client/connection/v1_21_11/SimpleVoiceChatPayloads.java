package dev.faspadio.pvsvcbridge.client.connection.v1_21_11;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

public final class SimpleVoiceChatPayloads {

    public static final CustomPayload.Id<RequestSecret> REQUEST_SECRET_ID =
            new CustomPayload.Id<>(BridgeIdentifiers.of("voicechat", "request_secret"));
    public static final CustomPayload.Id<SecretResponse> SECRET_ID =
            new CustomPayload.Id<>(BridgeIdentifiers.of("voicechat", "secret"));
    public static final CustomPayload.Id<UpdateState> UPDATE_STATE_ID =
            new CustomPayload.Id<>(BridgeIdentifiers.of("voicechat", "update_state"));
    public static final PacketCodec<RegistryByteBuf, RequestSecret> REQUEST_SECRET_CODEC =
            PacketCodec.of(RequestSecret::write, RequestSecret::new);
    public static final PacketCodec<RegistryByteBuf, SecretResponse> SECRET_CODEC =
            PacketCodec.of(SecretResponse::write, SecretResponse::new);
    public static final PacketCodec<RegistryByteBuf, UpdateState> UPDATE_STATE_CODEC =
            PacketCodec.of(UpdateState::write, UpdateState::new);
    private SimpleVoiceChatPayloads() {
    }

    public record RequestSecret(int compatibilityVersion) implements CustomPayload {

        public RequestSecret(RegistryByteBuf buf) {
            this(buf.readInt());
        }

        public void write(RegistryByteBuf buf) {
            buf.writeInt(compatibilityVersion);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return REQUEST_SECRET_ID;
        }
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
    ) implements CustomPayload {

        public SecretResponse(RegistryByteBuf buf) {
            this(
                    readSecret(buf),
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

        private static byte[] readSecret(RegistryByteBuf buf) {
            byte[] secret = new byte[16];
            buf.readBytes(secret);
            return secret;
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBytes(secret);
            buf.writeInt(serverPort);
            buf.writeUuid(playerId);
            buf.writeByte(codec);
            buf.writeInt(mtuSize);
            buf.writeDouble(voiceDistance);
            buf.writeInt(keepAliveMs);
            buf.writeBoolean(groupsEnabled);
            buf.writeString(voiceHost, 32767);
            buf.writeBoolean(recordingAllowed);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return SECRET_ID;
        }
    }

    public record UpdateState(boolean disabled) implements CustomPayload {

        public UpdateState(RegistryByteBuf buf) {
            this(buf.readBoolean());
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBoolean(disabled);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return UPDATE_STATE_ID;
        }
    }

}
