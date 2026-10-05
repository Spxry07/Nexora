package net.spxry.nexora.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public final class ResourceFiles {
    private static final String VERSION_KEY = "file-version";
    private static final String BACKUP_SUFFIX = ".old";

    private ResourceFiles() {}

    public static YamlConfiguration merged(Plugin plugin, String name) {
        var file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        var yaml = YamlConfiguration.loadConfiguration(file);
        var defaults = bundled(plugin, name);
        if (defaults == null) return yaml;
        if (defaults.getKeys(true).stream().allMatch(key -> yaml.contains(key, true))) return yaml;
        yaml.setDefaults(defaults);
        yaml.options().copyDefaults(true);
        save(plugin, yaml, file);
        return YamlConfiguration.loadConfiguration(file);
    }

    public static YamlConfiguration versioned(Plugin plugin, String name) {
        var file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        var yaml = YamlConfiguration.loadConfiguration(file);
        var defaults = bundled(plugin, name);
        if (defaults == null || yaml.getInt(VERSION_KEY) >= defaults.getInt(VERSION_KEY)) return yaml;
        try {
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), name + BACKUP_SUFFIX).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, name, e);
            return yaml;
        }
        plugin.saveResource(name, true);
        return YamlConfiguration.loadConfiguration(file);
    }

    private static YamlConfiguration bundled(Plugin plugin, String name) {
        try (var in = plugin.getResource(name)) {
            return in == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, name, e);
            return null;
        }
    }

    private static void save(Plugin plugin, YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, file.getName(), e);
        }
    }
}
