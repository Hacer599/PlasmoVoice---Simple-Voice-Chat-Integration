package dev.faspadio.pvsvcbridge.client.connection.v1_21_11;

import dev.faspadio.pvsvcbridge.common.log.BridgeLog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.function.Consumer;

public final class SimpleVoiceChatNetworking {

    private SimpleVoiceChatNetworking() {
    }

    public static boolean initialize(Consumer<SimpleVoiceChatPayloads.SecretResponse> secretReceiver) {
        try {
            PayloadTypeRegistry.playC2S().register(
                    SimpleVoiceChatPayloads.REQUEST_SECRET_ID,
                    SimpleVoiceChatPayloads.REQUEST_SECRET_CODEC
            );
            PayloadTypeRegistry.playC2S().register(
                    SimpleVoiceChatPayloads.UPDATE_STATE_ID,
                    SimpleVoiceChatPayloads.UPDATE_STATE_CODEC
            );
            PayloadTypeRegistry.playS2C().register(
                    SimpleVoiceChatPayloads.SECRET_ID,
                    SimpleVoiceChatPayloads.SECRET_CODEC
            );
        } catch (IllegalArgumentException collision) {
            BridgeLog.error("Simple Voice Chat payload registration collided with another installed mod", collision);
            return false;
        }
        ClientPlayNetworking.registerGlobalReceiver(
                SimpleVoiceChatPayloads.SECRET_ID,
                (payload, context) -> secretReceiver.accept(payload)
        );
        return true;
    }

    public static boolean canSendRequest() {
        return ClientPlayNetworking.canSend(SimpleVoiceChatPayloads.REQUEST_SECRET_ID);
    }

    public static void sendRequest(int compatibilityVersion) {
        ClientPlayNetworking.send(new SimpleVoiceChatPayloads.RequestSecret(compatibilityVersion));
    }

    public static void sendUpdateState(boolean disabled) {
        ClientPlayNetworking.send(new SimpleVoiceChatPayloads.UpdateState(disabled));
    }
}
