package dev.faspadio.pvsvcbridge.client.connection.v1_21_11;

import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;

import java.util.function.Consumer;

public final class SimpleVoiceChatNetworking {

    private SimpleVoiceChatNetworking() {
    }

    public static boolean initialize(Consumer<SimpleVoiceChatPayloads.SecretResponse> secretReceiver) {
        ClientPlayNetworking.registerGlobalReceiver(
                SimpleVoiceChatPayloads.SECRET_ID,
                (client, handler, buf, responseSender) -> secretReceiver.accept(
                        SimpleVoiceChatPayloads.SecretResponse.read(buf)
                )
        );
        BridgeLog.debug("Registered legacy Simple Voice Chat custom-payload receiver");
        return true;
    }

    public static boolean canSendRequest() {
        return ClientPlayNetworking.canSend(SimpleVoiceChatPayloads.REQUEST_SECRET_ID);
    }

    public static void sendRequest(int compatibilityVersion) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(compatibilityVersion);
        ClientPlayNetworking.send(SimpleVoiceChatPayloads.REQUEST_SECRET_ID, buf);
    }

    public static void sendUpdateState(boolean disabled) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(disabled);
        ClientPlayNetworking.send(SimpleVoiceChatPayloads.UPDATE_STATE_ID, buf);
    }
}
