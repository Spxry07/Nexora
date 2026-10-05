package net.spxry.nexora.npc;

import net.spxry.nexora.Nexora;
import net.spxry.nexora.config.MessageManager;
import net.spxry.nexora.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;

public final class ActionRunner {
    private static final String LINE_BREAK = "\\R";
    private static final String WHITESPACE = "\\s+";
    private static final String COMMAND_PREFIX = "/";
    private static final String PREFIX_PLACEHOLDER = "prefix";
    private static final float DEFAULT_VOLUME = 1F;
    private static final float DEFAULT_PITCH = 1F;
    private static final int SOUND_KEY = 0;
    private static final int SOUND_VOLUME = 1;
    private static final int SOUND_PITCH = 2;

    private final Nexora plugin;

    public ActionRunner(Nexora plugin) {
        this.plugin = plugin;
    }

    public void run(Player player, String actions, Map<String, String> placeholders) {
        if (actions == null || actions.isBlank()) return;
        Map<String, String> values = new HashMap<>(placeholders);
        values.putIfAbsent(PREFIX_PLACEHOLDER, plugin.messages().getPrefix());
        for (var raw : actions.split(LINE_BREAK)) {
            var line = raw.strip();
            if (!line.isEmpty()) dispatch(player, line, values);
        }
    }

    private void dispatch(Player player, String line, Map<String, String> values) {
        if (tagged(line, "actions.console")) console(payload(line, "actions.console", values));
        else if (tagged(line, "actions.player")) command(player, payload(line, "actions.player", values));
        else if (tagged(line, "actions.message")) player.sendMessage(ColorUtil.colorize(payload(line, "actions.message", values)));
        else if (tagged(line, "actions.actionbar")) player.sendActionBar(ColorUtil.colorize(payload(line, "actions.actionbar", values)));
        else if (tagged(line, "actions.sound")) sound(player, payload(line, "actions.sound", values));
    }

    private boolean tagged(String line, String path) {
        var tag = plugin.getConfig().getString(path, "");
        return !tag.isEmpty() && line.startsWith(tag);
    }

    private String payload(String line, String path, Map<String, String> values) {
        var tag = plugin.getConfig().getString(path, "");
        return MessageManager.apply(line.substring(tag.length()).strip(), values);
    }

    private String command(String text) {
        return text.startsWith(COMMAND_PREFIX) ? text.substring(COMMAND_PREFIX.length()) : text;
    }

    private void console(String text) {
        var command = command(text);
        if (command.isBlank()) return;
        plugin.scheduler().runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    private void command(Player player, String text) {
        var command = command(text);
        if (command.isBlank()) return;
        plugin.scheduler().runAtEntity(player, () -> player.performCommand(command));
    }

    private void sound(Player player, String text) {
        var parts = text.split(WHITESPACE);
        if (parts[SOUND_KEY].isEmpty()) return;
        float volume = number(parts, SOUND_VOLUME, DEFAULT_VOLUME);
        float pitch = number(parts, SOUND_PITCH, DEFAULT_PITCH);
        MessageManager.play(player, parts[SOUND_KEY], volume, pitch);
    }

    private float number(String[] parts, int index, float fallback) {
        if (parts.length <= index) return fallback;
        try {
            return Float.parseFloat(parts[index]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
