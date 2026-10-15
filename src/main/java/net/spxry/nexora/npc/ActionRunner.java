package net.spxry.nexora.npc;

import net.kyori.adventure.title.Title;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.config.MessageManager;
import net.spxry.nexora.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class ActionRunner {
    private enum Type { PLAYER, OP, CONSOLE, MESSAGE, BROADCAST, ACTIONBAR, TITLE, SOUND, SERVER, CLOSE, DELAY, PERMISSION, NONE }

    private record Step(Type type, String payload) {}

    private static final String ROOT = "actions.";
    private static final String KEY_UNTAGGED = ROOT + "untagged";
    private static final String KEY_PLAYER_CHAT = ROOT + "player-as-chat";
    private static final String KEY_ALLOW_OP = ROOT + "allow-op";
    private static final String KEY_PAPI = ROOT + "placeholderapi";
    private static final String KEY_TITLE_SPLIT = ROOT + "title-separator";
    private static final String KEY_TITLE_IN = ROOT + "title-fade-in";
    private static final String KEY_TITLE_STAY = ROOT + "title-stay";
    private static final String KEY_TITLE_OUT = ROOT + "title-fade-out";
    private static final String KEY_CHANNEL = ROOT + "proxy-channel";
    private static final String KEY_CONNECT = ROOT + "proxy-connect";
    private static final String KEY_MAX_DELAY = ROOT + "max-delay-ticks";
    private static final String PAPI_PLUGIN = "PlaceholderAPI";
    private static final String PAPI_CLASS = "me.clip.placeholderapi.PlaceholderAPI";
    private static final String PAPI_METHOD = "setPlaceholders";
    private static final char PAPI_MARK = '%';
    private static final String LINE_BREAK = "\\R";
    private static final String WHITESPACE = "\\s+";
    private static final String COMMAND_PREFIX = "/";
    private static final String PREFIX_PLACEHOLDER = "prefix";
    private static final float DEFAULT_VOLUME = 1F;
    private static final float DEFAULT_PITCH = 1F;
    private static final int SOUND_KEY = 0;
    private static final int SOUND_VOLUME = 1;
    private static final int SOUND_PITCH = 2;
    private static final int SPLIT_PAIR = 2;
    private static final long MILLIS_PER_TICK = 50L;

    private final Nexora plugin;
    private volatile Method papi;
    private volatile boolean papiFailed;

    public ActionRunner(Nexora plugin) {
        this.plugin = plugin;
        var channel = plugin.getConfig().getString(KEY_CHANNEL, "");
        if (!channel.isBlank()) plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
    }

    public void run(Player player, String actions, Map<String, String> placeholders) {
        if (player == null || actions == null || actions.isBlank()) return;
        Map<String, String> values = new HashMap<>(placeholders);
        values.putIfAbsent(PREFIX_PLACEHOLDER, plugin.messages().getPrefix());
        var tags = tags();
        var fallback = untagged();
        List<Step> steps = new ArrayList<>();
        for (var raw : actions.split(LINE_BREAK)) {
            var line = raw.strip();
            if (!line.isEmpty()) steps.add(step(line, tags, fallback));
        }
        if (steps.isEmpty()) return;
        plugin.scheduler().runAtEntity(player, () -> execute(player, steps, 0, values));
    }

    public void runConsole(String command, Map<String, String> placeholders) {
        console(MessageManager.apply(command, placeholders));
    }

    public void runPlayer(Player player, String command, Map<String, String> placeholders) {
        plugin.scheduler().runAtEntity(player, () -> playerCommand(player, resolve(player, command, placeholders)));
    }

    private void execute(Player player, List<Step> steps, int start, Map<String, String> values) {
        if (!player.isOnline()) return;
        for (int i = start; i < steps.size(); i++) {
            var step = steps.get(i);
            var text = resolve(player, step.payload(), values);
            switch (step.type()) {
                case DELAY -> {
                    long ticks = delay(text);
                    if (ticks <= 0) continue;
                    int next = i + 1;
                    plugin.scheduler().runAtEntityDelayed(player, () -> execute(player, steps, next, values), ticks);
                    return;
                }
                case PERMISSION -> {
                    if (!allowed(player, text)) return;
                }
                case PLAYER -> playerCommand(player, text);
                case OP -> opCommand(player, text);
                case CONSOLE -> console(text);
                case MESSAGE -> player.sendMessage(ColorUtil.colorize(text));
                case BROADCAST -> {
                    var message = ColorUtil.colorize(text);
                    plugin.scheduler().runGlobal(() -> Bukkit.broadcast(message));
                }
                case ACTIONBAR -> player.sendActionBar(ColorUtil.colorize(text));
                case TITLE -> title(player, text);
                case SOUND -> sound(player, text);
                case SERVER -> connect(player, text);
                case CLOSE -> player.closeInventory();
                case NONE -> {
                }
            }
        }
    }

    private Step step(String line, Map<String, Type> tags, Type fallback) {
        var lower = line.toLowerCase(Locale.ROOT);
        for (var entry : tags.entrySet()) {
            if (lower.startsWith(entry.getKey())) return new Step(entry.getValue(), line.substring(entry.getKey().length()).strip());
        }
        return new Step(fallback, line);
    }

    private Map<String, Type> tags() {
        Map<String, Type> out = new HashMap<>();
        var config = plugin.getConfig();
        var defaults = config.getDefaults();
        for (var type : Type.values()) {
            if (type == Type.NONE) continue;
            var path = ROOT + type.name().toLowerCase(Locale.ROOT);
            Set<String> names = new LinkedHashSet<>(values(config, path));
            if (defaults != null) names.addAll(values(defaults, path));
            for (var name : names) if (!name.isBlank()) out.putIfAbsent(name.strip().toLowerCase(Locale.ROOT), type);
        }
        Map<String, Type> sorted = new LinkedHashMap<>();
        out.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, Type> entry) -> entry.getKey().length()).reversed())
            .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private static List<String> values(ConfigurationSection config, String path) {
        if (config.isList(path)) return config.getStringList(path);
        var single = config.getString(path, "");
        return single.isBlank() ? List.of() : List.of(single);
    }

    private Type untagged() {
        try {
            return Type.valueOf(plugin.getConfig().getString(KEY_UNTAGGED, "").strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Type.NONE;
        }
    }

    private String resolve(Player player, String text, Map<String, String> values) {
        return placeholderApi(player, MessageManager.apply(text, values));
    }

    private String placeholderApi(Player player, String text) {
        if (papiFailed || text.indexOf(PAPI_MARK) < 0 || !plugin.getConfig().getBoolean(KEY_PAPI)) return text;
        if (!Bukkit.getPluginManager().isPluginEnabled(PAPI_PLUGIN)) return text;
        try {
            var method = papi;
            if (method == null) {
                method = Class.forName(PAPI_CLASS).getMethod(PAPI_METHOD, OfflinePlayer.class, String.class);
                papi = method;
            }
            return (String) method.invoke(null, player, text);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            papiFailed = true;
            plugin.getLogger().log(Level.WARNING, PAPI_PLUGIN, e);
            return text;
        }
    }

    private static String command(String text) {
        var trimmed = text.strip();
        return trimmed.startsWith(COMMAND_PREFIX) ? trimmed.substring(COMMAND_PREFIX.length()).strip() : trimmed;
    }

    private void console(String text) {
        var command = command(text);
        if (command.isBlank()) return;
        plugin.scheduler().runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    private void playerCommand(Player player, String text) {
        var command = command(text);
        if (command.isBlank() || !player.isOnline()) return;
        if (plugin.getConfig().getBoolean(KEY_PLAYER_CHAT)) player.chat(COMMAND_PREFIX + command);
        else player.performCommand(command);
    }

    private void opCommand(Player player, String text) {
        if (!plugin.getConfig().getBoolean(KEY_ALLOW_OP) || player.isOp()) {
            playerCommand(player, text);
            return;
        }
        player.setOp(true);
        try {
            playerCommand(player, text);
        } finally {
            player.setOp(false);
        }
    }

    private long delay(String text) {
        try {
            long max = Math.max(0, plugin.getConfig().getLong(KEY_MAX_DELAY));
            return Math.clamp(Long.parseLong(text.strip()), 0, max);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean allowed(Player player, String text) {
        var parts = text.strip().split(WHITESPACE, SPLIT_PAIR);
        if (parts[0].isEmpty() || player.hasPermission(parts[0])) return true;
        if (parts.length == SPLIT_PAIR && !parts[1].isBlank()) player.sendMessage(ColorUtil.colorize(parts[1]));
        return false;
    }

    private void title(Player player, String text) {
        var config = plugin.getConfig();
        var separator = config.getString(KEY_TITLE_SPLIT, "");
        String main = text;
        String sub = "";
        int at = separator.isEmpty() ? -1 : text.indexOf(separator);
        if (at >= 0) {
            main = text.substring(0, at);
            sub = text.substring(at + separator.length());
        }
        var times = Title.Times.times(ticks(config, KEY_TITLE_IN), ticks(config, KEY_TITLE_STAY), ticks(config, KEY_TITLE_OUT));
        player.showTitle(Title.title(ColorUtil.colorize(main.strip()), ColorUtil.colorize(sub.strip()), times));
    }

    private static Duration ticks(Configuration config, String key) {
        return Duration.ofMillis(Math.max(0, config.getLong(key)) * MILLIS_PER_TICK);
    }

    private void connect(Player player, String server) {
        var config = plugin.getConfig();
        var channel = config.getString(KEY_CHANNEL, "");
        var target = server.strip();
        if (channel.isBlank() || target.isEmpty()) return;
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeUTF(config.getString(KEY_CONNECT, ""));
            out.writeUTF(target);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, target, e);
            return;
        }
        player.sendPluginMessage(plugin, channel, bytes.toByteArray());
    }

    private void sound(Player player, String text) {
        var parts = text.strip().split(WHITESPACE);
        if (parts[SOUND_KEY].isEmpty()) return;
        float volume = number(parts, SOUND_VOLUME, DEFAULT_VOLUME);
        float pitch = number(parts, SOUND_PITCH, DEFAULT_PITCH);
        MessageManager.play(player, parts[SOUND_KEY], volume, pitch);
    }

    private static float number(String[] parts, int index, float fallback) {
        if (parts.length <= index) return fallback;
        try {
            return Float.parseFloat(parts[index]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
