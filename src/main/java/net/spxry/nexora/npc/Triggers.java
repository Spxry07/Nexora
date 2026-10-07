package net.spxry.nexora.npc;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.spxry.nexora.Nexora;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class Triggers {
    public enum Event {
        CLICK(true), RIGHTCLICK(true), LEFTCLICK(true), LOOK(true), LOOKAWAY(true), APPROACH(true), LEAVE(true), SPAWN(true),
        SIGNAL(false), HOLDING(false), FRAME(false), SWING(false), USE(false), USESTOP(false), INTERVAL(false);

        private final boolean playerScoped;

        Event(boolean playerScoped) {
            this.playerScoped = playerScoped;
        }

        public boolean playerScoped() { return playerScoped; }
    }

    public enum TextKind { MESSAGE, ACTIONBAR, TITLE }

    public enum Toggle { ON, OFF, TOGGLE }

    public enum Playback { ON, OFF, RESTART }

    public enum TargetKind { ANCHOR, PLAYER, NPC, HOLO, RELATIVE, ABSOLUTE }

    public record Target(TargetKind kind, String id, String anchor, double[] vec) {}

    public sealed interface Action {}

    public record ParticleAction(Particle type, Object data, int count, Target at, Target to, double[] offset, double spread, double speed, boolean all) implements Action {}

    public record SoundAction(Sound sound, boolean all) implements Action {}

    public record TextAction(TextKind kind, String text, String sub) implements Action {}

    public record CommandAction(boolean console, String command) implements Action {}

    public record SignalAction(String name) implements Action {}

    public record DelayAction(int ticks) implements Action {}

    public record HostAction(String verb, List<String> args) implements Action {}

    public record Rule(int index, Event event, Material material, int frame, int every, int cooldown, String name, List<Action> actions) {
        public boolean accepts(int value) { return frame < 0 || frame == value; }
    }

    private record Limits(int maxActions, int maxParticles, int maxDelay) {}

    private record Resolved(Particle type, Object data) {}

    public static final int UNSET_COOLDOWN = -1;
    public static final String ANCHOR_HEAD = "head";
    public static final String ANCHOR_CENTER = "center";
    public static final String ANCHOR_BODY = "body";
    public static final String ANCHOR_AROUND = "around";

    private static final String KEY_PREFIX = "triggers.";
    private static final String LEGACY_PREFIX = "npc.triggers.";
    private static final String MAX_RULES_KEY = "max-rules";
    private static final String MAX_ACTIONS_KEY = "max-actions";
    private static final String MAX_PARTICLES_KEY = "max-particles";
    private static final String MAX_DELAY_KEY = "max-delay-ticks";
    private static final int DEFAULT_MAX_RULES = 64;
    private static final int DEFAULT_MAX_ACTIONS = 16;
    private static final int DEFAULT_MAX_PARTICLES = 200;
    private static final int DEFAULT_MAX_DELAY = 6000;
    private static final int DEFAULT_HOLD_EVERY = 20;
    private static final int MIN_EVERY = 1;
    private static final int MIN_DELAY = 1;
    private static final int NO_FRAME = -1;
    private static final float MIN_SIZE = 0.01F;
    private static final float MAX_SIZE = 4F;
    private static final float DEFAULT_SIZE = 1F;
    private static final double DEFAULT_SPREAD = 0.25;
    private static final double DEFAULT_RING = 0.8;
    private static final double NO_SPREAD = 0;
    private static final float DEFAULT_VOLUME = 1F;
    private static final float DEFAULT_PITCH = 1F;
    private static final String COMMENT = "#";
    private static final String ARROW = "->";
    private static final String ACTION_SPLIT = ";";
    private static final String LINE_SPLIT = "\\R";
    private static final String WHITESPACE = "\\s+";
    private static final String SPEC_SPLIT = ":";
    private static final String VECTOR_SPLIT = ",";
    private static final String COLOR_PREFIX = "#";
    private static final String SUBTITLE_SPLIT = "\\|";
    private static final String ALL_SUFFIX = "!";
    private static final String RELATIVE_MARK = "~";
    private static final String LINE_ANCHOR_PREFIX = "line:";
    private static final String NPC_PREFIX = "npc:";
    private static final String HOLO_PREFIX = "holo:";
    private static final String TARGET_PLAYER = "player";
    private static final String DEFAULT_OBJECT_ANCHOR = ANCHOR_CENTER;
    private static final int OBJECT_SPEC_PARTS = 3;
    private static final int OBJECT_ID_INDEX = 1;
    private static final int OBJECT_ANCHOR_INDEX = 2;
    private static final int VECTOR_SIZE = 3;
    private static final String KEY_EVERY = "every";
    private static final String KEY_COOLDOWN = "cooldown";
    private static final String KEY_AT = "at";
    private static final String KEY_TO = "to";
    private static final String KEY_OFFSET = "offset";
    private static final String NONE = "none";
    private static final String AIR = "air";
    private static final String VERB_PARTICLE = "particle";
    private static final String VERB_SOUND = "sound";
    private static final String VERB_MESSAGE = "message";
    private static final String VERB_ACTIONBAR = "actionbar";
    private static final String VERB_TITLE = "title";
    private static final String VERB_CONSOLE = "console";
    private static final String VERB_PLAYER = "player";
    private static final String VERB_SIGNAL = "signal";
    private static final String VERB_DELAY = "delay";
    private static final String KIND_DUST = "DUST";
    private static final String KIND_TRANSITION = "DUST_TRANSITION";
    private static final String KIND_ITEM = "ITEM";
    private static final String KIND_BLOCK = "BLOCK";
    private static final int HEX_LENGTH = 6;
    private static final int HEX_RADIX = 16;
    private static final int SPEC_FIRST = 1;
    private static final int SPEC_SECOND = 2;
    private static final int SPEC_THIRD = 3;
    private static final Pattern SIGNAL_NAME = Pattern.compile("[a-z0-9_.-]{1,48}");
    private static final Pattern VERB_NAME = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    private static final Set<String> ANCHORS = Set.of(ANCHOR_HEAD, "hand", "offhand", "feet", ANCHOR_BODY, "above", ANCHOR_CENTER, "top", "bottom", ANCHOR_AROUND);

    private Triggers() {}

    public static String key(Nexora plugin, String name) {
        var config = plugin.getConfig();
        var current = KEY_PREFIX + name;
        return config.isSet(current) ? current : LEGACY_PREFIX + name;
    }

    public static String signalName(String raw) {
        if (raw == null) return null;
        var name = raw.strip().toLowerCase(Locale.ROOT);
        return SIGNAL_NAME.matcher(name).matches() ? name : null;
    }

    public static Map<Event, List<Rule>> parse(Nexora plugin, String text) {
        Map<Event, List<Rule>> grouped = new EnumMap<>(Event.class);
        if (text == null || text.isBlank()) return grouped;
        var config = plugin.getConfig();
        int maxRules = config.getInt(key(plugin, MAX_RULES_KEY), DEFAULT_MAX_RULES);
        var limits = new Limits(
            config.getInt(key(plugin, MAX_ACTIONS_KEY), DEFAULT_MAX_ACTIONS),
            config.getInt(key(plugin, MAX_PARTICLES_KEY), DEFAULT_MAX_PARTICLES),
            config.getInt(key(plugin, MAX_DELAY_KEY), DEFAULT_MAX_DELAY));
        int count = 0;
        for (var raw : text.split(LINE_SPLIT)) {
            if (count >= maxRules) break;
            Rule rule;
            try {
                rule = rule(raw.strip(), count, limits);
            } catch (RuntimeException e) {
                rule = null;
            }
            if (rule == null) continue;
            grouped.computeIfAbsent(rule.event(), e -> new ArrayList<>()).add(rule);
            count++;
        }
        grouped.replaceAll((event, list) -> List.copyOf(list));
        return Collections.unmodifiableMap(grouped);
    }

    private static Rule rule(String line, int index, Limits limits) {
        if (line.isEmpty() || line.startsWith(COMMENT)) return null;
        int arrow = line.indexOf(ARROW);
        if (arrow <= 0) return null;
        var head = line.substring(0, arrow).strip().split(WHITESPACE);
        var event = event(head[0]);
        if (event == null) return null;
        var actions = actions(event, line.substring(arrow + ARROW.length()), limits);
        if (actions.isEmpty()) return null;
        return header(event, head, index, actions);
    }

    private static Event event(String token) {
        try {
            return Event.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Rule header(Event event, String[] head, int index, List<Action> actions) {
        int at = 1;
        Material material = null;
        int frame = NO_FRAME;
        int every = 0;
        String name = "";
        switch (event) {
            case HOLDING -> {
                if (head.length < 2) return null;
                material = held(head[1]);
                if (material == null) return null;
                at = 2;
                every = DEFAULT_HOLD_EVERY;
            }
            case FRAME -> {
                var n = integer(head, 1);
                if (n == null || n < 1) return null;
                frame = n - 1;
                at = 2;
            }
            case INTERVAL -> {
                var n = integer(head, 1);
                if (n == null || n < 1) return null;
                every = n;
                at = 2;
            }
            case SIGNAL -> {
                if (head.length < 2) return null;
                name = signalName(head[1]);
                if (name == null) return null;
                at = 2;
            }
            default -> {
            }
        }
        int cooldown = UNSET_COOLDOWN;
        for (; at < head.length; at += 2) {
            var value = integer(head, at + 1);
            if (value == null || value < 0) return null;
            switch (head[at].toLowerCase(Locale.ROOT)) {
                case KEY_EVERY -> {
                    if (event != Event.HOLDING && event != Event.INTERVAL) return null;
                    every = Math.max(MIN_EVERY, value);
                }
                case KEY_COOLDOWN -> cooldown = value;
                default -> {
                    return null;
                }
            }
        }
        return new Rule(index, event, material, frame, every, cooldown, name, actions);
    }

    private static Integer integer(String[] tokens, int index) {
        if (index >= tokens.length) return null;
        try {
            return Integer.parseInt(tokens[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Material held(String token) {
        var lower = token.toLowerCase(Locale.ROOT);
        if (lower.equals(NONE) || lower.equals(AIR)) return Material.AIR;
        var material = Material.matchMaterial(token);
        return material != null && material.isItem() ? material : null;
    }

    private static List<Action> actions(Event event, String text, Limits limits) {
        List<Action> list = new ArrayList<>();
        for (var raw : text.split(ACTION_SPLIT)) {
            if (list.size() >= limits.maxActions()) break;
            var part = raw.strip();
            if (part.isEmpty()) continue;
            Action action;
            try {
                action = action(event, part, limits);
            } catch (RuntimeException e) {
                action = null;
            }
            if (action != null) list.add(action);
        }
        return List.copyOf(list);
    }

    private static Action action(Event event, String text, Limits limits) {
        int space = indexOfWhitespace(text);
        var verb = space < 0 ? text : text.substring(0, space);
        var rest = space < 0 ? "" : text.substring(space).strip();
        boolean all = verb.endsWith(ALL_SUFFIX);
        if (all) verb = verb.substring(0, verb.length() - ALL_SUFFIX.length());
        boolean commands = event.playerScoped() || event == Event.SIGNAL;
        var lower = verb.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case VERB_PARTICLE -> particle(rest, all, limits);
            case VERB_SOUND -> sound(rest, all);
            case VERB_MESSAGE -> rest.isEmpty() ? null : new TextAction(TextKind.MESSAGE, rest, "");
            case VERB_ACTIONBAR -> rest.isEmpty() ? null : new TextAction(TextKind.ACTIONBAR, rest, "");
            case VERB_TITLE -> title(rest);
            case VERB_CONSOLE -> commands && !rest.isEmpty() ? new CommandAction(true, rest) : null;
            case VERB_PLAYER -> commands && !rest.isEmpty() ? new CommandAction(false, rest) : null;
            case VERB_SIGNAL -> signal(rest);
            case VERB_DELAY -> delay(rest, limits);
            default -> host(lower, rest);
        };
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) if (Character.isWhitespace(text.charAt(i))) return i;
        return -1;
    }

    private static Action signal(String rest) {
        var name = signalName(rest);
        return name == null ? null : new SignalAction(name);
    }

    private static Action delay(String rest, Limits limits) {
        var token = rest.split(WHITESPACE)[0];
        int ticks = Integer.parseInt(token);
        return new DelayAction(Math.clamp(ticks, MIN_DELAY, Math.max(MIN_DELAY, limits.maxDelay())));
    }

    private static Action host(String verb, String rest) {
        if (!VERB_NAME.matcher(verb).matches()) return null;
        return new HostAction(verb, rest.isEmpty() ? List.of() : List.of(rest.split(WHITESPACE)));
    }

    private static Action title(String rest) {
        if (rest.isEmpty()) return null;
        var parts = rest.split(SUBTITLE_SPLIT, 2);
        return new TextAction(TextKind.TITLE, parts[0].strip(), parts.length > 1 ? parts[1].strip() : "");
    }

    private static Action sound(String rest, boolean all) {
        var tokens = rest.split(WHITESPACE);
        if (tokens[0].isEmpty()) return null;
        try {
            var key = Key.key(tokens[0].toLowerCase(Locale.ROOT));
            float volume = tokens.length > 1 ? Float.parseFloat(tokens[1]) : DEFAULT_VOLUME;
            float pitch = tokens.length > 2 ? Float.parseFloat(tokens[2]) : DEFAULT_PITCH;
            return new SoundAction(Sound.sound(key, Sound.Source.MASTER, volume, pitch), all);
        } catch (InvalidKeyException | NumberFormatException e) {
            return null;
        }
    }

    private static Action particle(String rest, boolean all, Limits limits) {
        var tokens = rest.split(WHITESPACE);
        if (tokens[0].isEmpty()) return null;
        var resolved = resolve(tokens[0].split(SPEC_SPLIT));
        if (resolved == null) return null;
        int count = 1;
        boolean countSet = false;
        boolean spreadSet = false;
        double spread = 0;
        double speed = 0;
        Target at = null;
        Target to = null;
        double[] offset = new double[VECTOR_SIZE];
        for (int i = 1; i < tokens.length; i++) {
            var token = tokens[i];
            var lower = token.toLowerCase(Locale.ROOT);
            switch (lower) {
                case KEY_AT -> {
                    if (++i >= tokens.length) return null;
                    at = target(tokens[i]);
                    if (at == null) return null;
                }
                case KEY_TO -> {
                    if (++i >= tokens.length) return null;
                    to = target(tokens[i]);
                    if (to == null) return null;
                }
                case KEY_OFFSET -> {
                    if (++i >= tokens.length) return null;
                    offset = vector(tokens[i], false);
                    if (offset == null) return null;
                }
                default -> {
                    if (isAnchor(lower)) {
                        at = new Target(TargetKind.ANCHOR, "", lower, null);
                    } else if (!countSet && token.chars().allMatch(Character::isDigit)) {
                        count = Integer.parseInt(token);
                        countSet = true;
                    } else if (!spreadSet) {
                        spread = Math.max(0, Double.parseDouble(token));
                        spreadSet = true;
                    } else {
                        speed = Math.max(0, Double.parseDouble(token));
                    }
                }
            }
        }
        if (at == null) at = new Target(TargetKind.ANCHOR, "", ANCHOR_HEAD, null);
        boolean around = at.kind() == TargetKind.ANCHOR && ANCHOR_AROUND.equals(at.anchor());
        if (!spreadSet) spread = around ? DEFAULT_RING : to != null ? NO_SPREAD : DEFAULT_SPREAD;
        count = Math.clamp(count, 1, Math.max(1, limits.maxParticles()));
        return new ParticleAction(resolved.type(), resolved.data(), count, at, to, offset, spread, speed, all);
    }

    private static boolean isAnchor(String lower) {
        return ANCHORS.contains(lower) || lower.startsWith(LINE_ANCHOR_PREFIX);
    }

    private static Target target(String token) {
        var lower = token.toLowerCase(Locale.ROOT);
        if (lower.equals(TARGET_PLAYER)) return new Target(TargetKind.PLAYER, "", "", null);
        if (lower.startsWith(NPC_PREFIX)) return object(TargetKind.NPC, token);
        if (lower.startsWith(HOLO_PREFIX)) return object(TargetKind.HOLO, token);
        if (lower.startsWith(RELATIVE_MARK)) return vectorTarget(TargetKind.RELATIVE, lower, true);
        if (lower.contains(VECTOR_SPLIT)) return vectorTarget(TargetKind.ABSOLUTE, lower, false);
        return isAnchor(lower) ? new Target(TargetKind.ANCHOR, "", lower, null) : null;
    }

    private static Target object(TargetKind kind, String token) {
        var parts = token.split(SPEC_SPLIT, OBJECT_SPEC_PARTS);
        if (parts.length <= OBJECT_ID_INDEX || parts[OBJECT_ID_INDEX].isEmpty()) return null;
        boolean named = parts.length > OBJECT_ANCHOR_INDEX && !parts[OBJECT_ANCHOR_INDEX].isEmpty();
        var anchor = named ? parts[OBJECT_ANCHOR_INDEX].toLowerCase(Locale.ROOT) : DEFAULT_OBJECT_ANCHOR;
        return new Target(kind, parts[OBJECT_ID_INDEX], anchor, null);
    }

    private static Target vectorTarget(TargetKind kind, String token, boolean relative) {
        var vec = vector(token, relative);
        return vec == null ? null : new Target(kind, "", "", vec);
    }

    private static double[] vector(String token, boolean relative) {
        var parts = token.split(VECTOR_SPLIT, -1);
        if (parts.length != VECTOR_SIZE) return null;
        double[] out = new double[VECTOR_SIZE];
        for (int i = 0; i < VECTOR_SIZE; i++) {
            var part = parts[i].strip();
            if (relative) {
                if (!part.startsWith(RELATIVE_MARK)) return null;
                part = part.substring(RELATIVE_MARK.length());
                out[i] = part.isEmpty() ? 0 : Double.parseDouble(part);
            } else {
                out[i] = Double.parseDouble(part);
            }
            if (!Double.isFinite(out[i])) return null;
        }
        return out;
    }

    private static Resolved resolve(String[] spec) {
        var kind = spec[0].toUpperCase(Locale.ROOT);
        return switch (kind) {
            case KIND_DUST -> {
                var color = color(spec, SPEC_FIRST);
                yield color == null ? null : new Resolved(Particle.DUST, new Particle.DustOptions(color, size(spec, SPEC_SECOND)));
            }
            case KIND_TRANSITION -> {
                var from = color(spec, SPEC_FIRST);
                var to = color(spec, SPEC_SECOND);
                yield from == null || to == null ? null
                    : new Resolved(Particle.DUST_COLOR_TRANSITION, new Particle.DustTransition(from, to, size(spec, SPEC_THIRD)));
            }
            case KIND_ITEM -> {
                var material = material(spec, SPEC_FIRST);
                yield material == null || !material.isItem() || material.isAir() ? null : new Resolved(Particle.ITEM, new ItemStack(material));
            }
            case KIND_BLOCK -> {
                var material = material(spec, SPEC_FIRST);
                yield material == null || !material.isBlock() || material.isAir() ? null : new Resolved(Particle.BLOCK, material.createBlockData());
            }
            default -> {
                var type = Particle.valueOf(kind);
                yield type.getDataType() == Void.class ? new Resolved(type, null) : null;
            }
        };
    }

    private static Material material(String[] spec, int index) {
        return index < spec.length ? Material.matchMaterial(spec[index]) : null;
    }

    private static Color color(String[] spec, int index) {
        if (index >= spec.length) return null;
        var hex = spec[index].startsWith(COLOR_PREFIX) ? spec[index].substring(COLOR_PREFIX.length()) : spec[index];
        if (hex.length() != HEX_LENGTH) return null;
        return Color.fromRGB(Integer.parseInt(hex, HEX_RADIX));
    }

    private static float size(String[] spec, int index) {
        if (index >= spec.length) return DEFAULT_SIZE;
        return Math.clamp(Float.parseFloat(spec[index]), MIN_SIZE, MAX_SIZE);
    }
}
