package dev.faspadio.pvsvcbridge.common.text;

import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.TranslatableText;

public final class BridgeTexts {

    private BridgeTexts() {
    }

    public static MutableText literal(String value) {
        return new LiteralText(value);
    }

    public static MutableText translatable(String key, Object... arguments) {
        return new TranslatableText(key, arguments);
    }
}
