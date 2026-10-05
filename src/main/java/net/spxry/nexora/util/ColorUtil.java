package net.spxry.nexora.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ColorUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .hexColors().useUnusualXRepeatedCharacterHexFormat().character('&').build();

    private ColorUtil() {}

    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return LEGACY.deserialize(text);
    }

    public static String stripColor(String text) {
        if (text == null) return "";
        return LegacyComponentSerializer.legacyAmpersand().serialize(LEGACY.deserialize(text))
            .replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }

    public static String plain(String text) {
        if (text == null || text.isEmpty()) return "";
        return PlainTextComponentSerializer.plainText().serialize(LEGACY.deserialize(text));
    }

    public static String toLegacy(Component component) { return LEGACY.serialize(component); }
}
