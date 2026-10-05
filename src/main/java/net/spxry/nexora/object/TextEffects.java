package net.spxry.nexora.object;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class TextEffects {
    private enum Effect { NONE, SOLID, FLICKER, FADE, TYPEWRITER, RAINBOW, WAVE, SCROLL, BLINK }

    private enum Kind { CODE, CHAR, BREAK }

    private record Token(String text, Kind kind) {}

    private static final String ALL_CODES = "0123456789abcdefklmnor";
    private static final String COLOR_CODES = "0123456789abcdef";
    private static final String FORMAT_CODES = "klmno";
    private static final char RESET_CODE = 'r';
    private static final char CODE_PREFIX = '&';
    private static final char HEX_MARK = '#';
    private static final char SPACE = ' ';
    private static final char BREAK = '\n';
    private static final int HEX_DIGITS = 6;
    private static final int HEX_CODE_LENGTH = 8;
    private static final int SIMPLE_CODE_LENGTH = 2;
    private static final int HEX_RADIX = 16;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int OPAQUE = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ARGB_LENGTH = 8;
    private static final int CHANNEL_MAX = 255;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int BLINK_PHASES = 2;
    private static final int HUE_SECTORS = 6;
    private static final double HALF = 0.5;
    private static final long HASH_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long HASH_MIX_A = 0xBF58476D1CE4E5B9L;
    private static final long HASH_MIX_B = 0x94D049BB133111EBL;
    private static final int HASH_SHIFT_A = 30;
    private static final int HASH_SHIFT_B = 27;
    private static final int HASH_SHIFT_C = 31;
    private static final int UNIT_SHIFT = 11;
    private static final double UNIT_SCALE = 0x1.0p-53;
    private static final String PLACEHOLDER_OPEN = "{";
    private static final String ONLINE = "{online}";
    private static final String MAX = "{max}";
    private static final String TIME = "{time}";
    private static final String DATE = "{date}";

    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    private TextEffects() {}

    static boolean dynamic(FileConfiguration config, HoloLine line) {
        if (!HoloLine.TEXT.equalsIgnoreCase(line.type)) return false;
        var separator = config.getString("animation.frame-separator", "");
        var effect = effectOf(line.effect);
        return effect != Effect.NONE && effect != Effect.SOLID
            ||!separator.isEmpty() && line.text.contains(separator)
            || line.text.contains(PLACEHOLDER_OPEN);
    }

    static int argb(String hex) {
        if (hex == null) return WHITE;
        var digits = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            long value = Long.parseLong(digits, HEX_RADIX);
            return digits.length() == ARGB_LENGTH ? (int) value : OPAQUE | (int) value;
        } catch (NumberFormatException e) {
            return WHITE;
        }
    }

    public static String render(FileConfiguration config, HoloLine line, long ticks) {
        var frames = frames(config, line.text);
        long interval = Math.max(1, line.frameInterval);
        int index = (int) ((ticks / interval) % frames.length);
        var text = placeholders(config, frames[index]);
        long speed = Math.max(1, line.effectSpeed);
        long step = ticks / speed;
        return switch (effectOf(line.effect)) {
            case NONE -> text;
            case SOLID -> flat(text, argb(line.colorA) & RGB_MASK);
            case FLICKER -> flat(text, flickerColor(config, line, step));
            case FADE -> flat(text, fadeColor(config, line, step));
            case TYPEWRITER -> typewriter(config, text, frames.length > 1 ? (ticks % interval) / speed : step);
            case RAINBOW -> rainbow(config, text, step);
            case WAVE -> wave(config, line, text, step);
            case SCROLL -> scroll(config, line, text, step);
            case BLINK -> step % BLINK_PHASES == 0 ? text : "";
        };
    }

    private static Effect effectOf(String name) {
        try {
            return Effect.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Effect.NONE;
        }
    }

    private static String[] frames(FileConfiguration config, String text) {
        var separator = config.getString("animation.frame-separator", "");
        if (separator.isEmpty()) return new String[]{text};
        var frames = text.split(Pattern.quote(separator), -1);
        for (int i = 0; i < frames.length; i++) frames[i] = frames[i].strip();
        return frames;
    }

    private static String placeholders(FileConfiguration config, String text) {
        if (!text.contains(PLACEHOLDER_OPEN)) return text;
        var result = replace(text, ONLINE, () -> String.valueOf(Bukkit.getOnlinePlayers().size()));
        result = replace(result, MAX, () -> String.valueOf(Bukkit.getMaxPlayers()));
        result = replace(result, TIME, () -> LocalDateTime.now().format(formatter(config.getString("placeholders.time-format"), DateTimeFormatter.ISO_LOCAL_TIME)));
        return replace(result, DATE, () -> LocalDateTime.now().format(formatter(config.getString("placeholders.date-format"), DateTimeFormatter.ISO_LOCAL_DATE)));
    }

    private static String replace(String text, String key, Supplier<String> value) {
        return text.contains(key) ? text.replace(key, value.get()) : text;
    }

    private static DateTimeFormatter formatter(String pattern, DateTimeFormatter fallback) {
        if (pattern == null) return fallback;
        return FORMATTERS.computeIfAbsent(pattern, p -> {
            try {
                return DateTimeFormatter.ofPattern(p);
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        });
    }

    private static String flat(String text, int rgb) {
        var kept = new ArrayList<Token>();
        for (var token : tokenize(text)) {
            if (token.kind() != Kind.CODE || !isColorCode(token.text())) kept.add(token);
        }
        return colored(kept, (i, total) -> rgb);
    }

    private static int flickerColor(FileConfiguration config, HoloLine line, long step) {
        boolean alternate = unitHash(step, System.identityHashCode(line)) < config.getDouble("effects.flicker-chance");
        return argb(alternate ? line.colorB : line.colorA) & RGB_MASK;
    }

    private static int fadeColor(FileConfiguration config, HoloLine line, long step) {
        double phase = HALF + HALF * Math.sin(step * config.getDouble("effects.fade-step") * Math.TAU);
        return mix(argb(line.colorA) & RGB_MASK, argb(line.colorB) & RGB_MASK, phase);
    }

    private static double unitHash(long step, int identity) {
        long h = step * HASH_GAMMA + identity;
        h = (h ^ (h >>> HASH_SHIFT_A)) * HASH_MIX_A;
        h = (h ^ (h >>> HASH_SHIFT_B)) * HASH_MIX_B;
        h ^= h >>> HASH_SHIFT_C;
        return (h >>> UNIT_SHIFT) * UNIT_SCALE;
    }

    private static boolean isColorCode(String code) {
        char c = Character.toLowerCase(code.charAt(1));
        return c == HEX_MARK || COLOR_CODES.indexOf(c) >= 0;
    }

    private static String typewriter(FileConfiguration config, String text, long step) {
        var tokens = tokenize(text);
        int total = visible(tokens);
        int cycle = Math.max(1, total + config.getInt("effects.typewriter-hold-steps"));
        int reveal = (int) (step % cycle);
        var out = new StringBuilder(text.length());
        int shown = 0;
        for (var token : tokens) {
            if (token.kind() != Kind.CHAR || shown++ < reveal) out.append(token.text());
        }
        return out.toString();
    }

    private static String rainbow(FileConfiguration config, String text, long step) {
        double stepSize = config.getDouble("effects.rainbow-step");
        double spread = config.getDouble("effects.rainbow-spread");
        double saturation = config.getDouble("effects.rainbow-saturation");
        double brightness = config.getDouble("effects.rainbow-brightness");
        return colored(tokenize(text), (i, total) -> hsv(step * stepSize + i * spread, saturation, brightness));
    }

    private static String wave(FileConfiguration config, HoloLine line, String text, long step) {
        double stepSize = config.getDouble("effects.wave-step");
        double spread = config.getDouble("effects.wave-spread");
        int a = argb(line.colorA) & RGB_MASK;
        int b = argb(line.colorB) & RGB_MASK;
        return colored(tokenize(text), (i, total) -> mix(a, b, (Math.sin(step * stepSize - i * spread) + 1) / 2));
    }

    private static String scroll(FileConfiguration config, HoloLine line, String text, long step) {
        var gap = config.getString("effects.scroll-gap", "");
        int width = Math.max(1, line.scrollWidth);
        var plain = new StringBuilder();
        for (var token : tokenize(text)) if (token.kind() != Kind.CODE) plain.append(token.text());
        var lines = plain.toString().split(String.valueOf(BREAK), -1);
        var out = new StringBuilder();
        appendHex(out, argb(line.colorA));
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append(BREAK);
            out.append(window(lines[i], gap, width, step));
        }
        return out.toString();
    }

    private static String window(String line, String gap, int width, long step) {
        if (line.length() <= width) return line;
        var full = line + gap;
        int offset = (int) (step % full.length());
        var out = new StringBuilder(width);
        for (int i = 0; i < width; i++) out.append(full.charAt((offset + i) % full.length()));
        return out.toString();
    }

    private static String colored(List<Token> tokens, IntBinaryOperator color) {
        int total = visible(tokens);
        var out = new StringBuilder();
        var formats = new StringBuilder();
        int index = 0;
        for (var token : tokens) {
            switch (token.kind()) {
                case BREAK -> out.append(token.text());
                case CODE -> track(formats, token.text());
                case CHAR -> {
                    int position = index++;
                    if (token.text().charAt(0) == SPACE) {
                        out.append(SPACE);
                    } else {
                        appendHex(out, color.applyAsInt(position, total));
                        out.append(formats).append(token.text());
                    }
                }
            }
        }
        return out.toString();
    }

    private static void track(StringBuilder formats, String code) {
        char c = Character.toLowerCase(code.charAt(1));
        if (c == HEX_MARK || c == RESET_CODE || COLOR_CODES.indexOf(c) >= 0) {
            formats.setLength(0);
        } else if (FORMAT_CODES.indexOf(c) >= 0 && formats.indexOf(code) < 0) {
            formats.append(code);
        }
    }

    private static void appendHex(StringBuilder out, int rgb) {
        var hex = Integer.toHexString(rgb & RGB_MASK);
        out.append(CODE_PREFIX).append(HEX_MARK);
        for (int i = hex.length(); i < HEX_DIGITS; i++) out.append('0');
        out.append(hex);
    }

    private static int visible(List<Token> tokens) {
        int count = 0;
        for (var token : tokens) if (token.kind() == Kind.CHAR) count++;
        return count;
    }

    private static List<Token> tokenize(String text) {
        List<Token> out = new ArrayList<>(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int length = c == CODE_PREFIX ? codeLength(text, i) : 0;
            if (length > 0) {
                out.add(new Token(text.substring(i, i + length), Kind.CODE));
                i += length;
            } else {
                out.add(new Token(String.valueOf(c), c == BREAK ? Kind.BREAK : Kind.CHAR));
                i++;
            }
        }
        return out;
    }

    private static int codeLength(String text, int start) {
        if (start + 1 >= text.length()) return 0;
        char next = text.charAt(start + 1);
        if (next != HEX_MARK) return ALL_CODES.indexOf(Character.toLowerCase(next)) >= 0 ? SIMPLE_CODE_LENGTH : 0;
        if (start + HEX_CODE_LENGTH > text.length()) return 0;
        for (int i = start + SIMPLE_CODE_LENGTH; i < start + HEX_CODE_LENGTH; i++) {
            if (Character.digit(text.charAt(i), HEX_RADIX) < 0) return 0;
        }
        return HEX_CODE_LENGTH;
    }

    private static int mix(int a, int b, double t) {
        int red = lerp(a >> RED_SHIFT, b >> RED_SHIFT, t);
        int green = lerp(a >> GREEN_SHIFT, b >> GREEN_SHIFT, t);
        int blue = lerp(a, b, t);
        return red << RED_SHIFT | green << GREEN_SHIFT | blue;
    }

    private static int lerp(int a, int b, double t) {
        int from = a & CHANNEL_MAX;
        int to = b & CHANNEL_MAX;
        return (int) Math.round(from + (to - from) * t);
    }

    private static int hsv(double hue, double saturation, double value) {
        double h = (hue - Math.floor(hue)) * HUE_SECTORS;
        int sector = (int) h;
        double f = h - sector;
        double p = value * (1 - saturation);
        double q = value * (1 - saturation * f);
        double t = value * (1 - saturation * (1 - f));
        double[] rgb = switch (sector) {
            case 0 -> new double[]{value, t, p};
            case 1 -> new double[]{q, value, p};
            case 2 -> new double[]{p, value, t};
            case 3 -> new double[]{p, q, value};
            case 4 -> new double[]{t, p, value};
            default -> new double[]{value, p, q};
        };
        return channel(rgb[0]) << RED_SHIFT | channel(rgb[1]) << GREEN_SHIFT | channel(rgb[2]);
    }

    private static int channel(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * CHANNEL_MAX);
    }
}
