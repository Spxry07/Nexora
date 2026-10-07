package net.spxry.nexora.gui;

import java.util.Locale;

final class SmallCaps {
    private static final String LATIN = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘQʀꜱᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() {}

    static String of(String text) {
        if (text == null) return "";
        var builder = new StringBuilder(text.length());
        for (var c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            var index = LATIN.indexOf(c);
            builder.append(index < 0 ? c : SMALL.charAt(index));
        }
        return builder.toString();
    }
}
