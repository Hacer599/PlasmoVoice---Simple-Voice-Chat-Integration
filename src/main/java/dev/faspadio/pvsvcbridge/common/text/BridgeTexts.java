package dev.faspadio.pvsvcbridge.common.text;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class BridgeTexts {

    private BridgeTexts() {
    }

    public static MutableText literal(String value) {
        return Text.literal(value);
    }

    public static MutableText translatable(String key, Object... arguments) {
        return Text.translatable(key, arguments);
    }
}
