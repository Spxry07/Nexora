package net.spxry.nexora.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.spxry.nexora.util.ColorUtil;
import net.spxry.nexora.util.ResourceFiles;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class MessageManager {
    private final Plugin plugin;
    private volatile FileConfiguration messages;

    public MessageManager(Plugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        this.messages = ResourceFiles.merged(plugin, "messages.yml");
    }

    public String getPrefix() { return messages.getString("prefix", ""); }

    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }

    public void send(CommandSender sender, String key, Map<String, String> ph) {
        var m = messages;
        if (!m.getBoolean(key + ".overall-enabled", true)) return;
        if (m.getBoolean(key + ".messages-enabled", true)) {
            for (var line : m.getStringList(key + ".messages")) {
                sender.sendMessage(ColorUtil.colorize(apply(line, ph).replace("{prefix}", getPrefix())));
            }
        }
        if (!(sender instanceof Player player)) return;
        if (m.getBoolean(key + ".actionbar-enabled", false)) {
            var raw = m.getString(key + ".Actionbar", "");
            if (!raw.isEmpty()) player.sendActionBar(ColorUtil.colorize(apply(raw, ph)));
        }
        if (m.getBoolean(key + ".title-enabled", false)) {
            var t = ColorUtil.colorize(apply(m.getString(key + ".title", ""), ph));
            var s = ColorUtil.colorize(apply(m.getString(key + ".subtitle", ""), ph));
            player.showTitle(Title.title(t, s, Title.Times.times(
                Duration.ofMillis(m.getInt(key + ".title-fadein", 10) * 50L),
                Duration.ofMillis(m.getInt(key + ".title-stay", 70) * 50L),
                Duration.ofMillis(m.getInt(key + ".title-fadeout", 20) * 50L))));
        }
        if (m.getBoolean(key + ".sound-enabled", true)) {
            play(player, m.getString(key + ".sound", ""),
                (float) m.getDouble(key + ".sound-volume", 1.0), (float) m.getDouble(key + ".sound-pitch", 1.0));
        }
    }

    public static void play(Audience audience, String sound, float volume, float pitch) {
        if (sound == null || sound.isBlank()) return;
        try {
            audience.playSound(Sound.sound(Key.key(sound.trim().toLowerCase()), Sound.Source.MASTER, volume, pitch));
        } catch (InvalidKeyException ignored) {
        }
    }

    public Component component(String key) { return component(key, Map.of()); }

    public Component component(String key, Map<String, String> ph) {
        return ColorUtil.colorize(apply(messages.getString(key, ""), ph).replace("{prefix}", getPrefix()));
    }

    public boolean has(String key) { return !messages.getString(key, "").isEmpty(); }

    public String raw(String key) { return messages.getString(key, ""); }

    public String raw(String key, Map<String, String> ph) { return apply(raw(key), ph); }

    public List<String> list(String key) { return messages.getStringList(key); }

    public static String apply(String text, Map<String, String> ph) {
        if (text == null) return "";
        var result = text;
        for (var e : ph.entrySet()) result = result.replace("{" + e.getKey() + "}", e.getValue());
        return result;
    }
}
