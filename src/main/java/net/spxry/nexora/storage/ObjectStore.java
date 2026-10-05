package net.spxry.nexora.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class ObjectStore {
    public record Entry(String kind, String id, ConfigurationSection data) {}

    private final Plugin plugin;
    private final ExecutorService io;
    private final Map<String, YamlConfiguration> files = new ConcurrentHashMap<>();

    public ObjectStore(Plugin plugin) {
        this.plugin = plugin;
        this.io = Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "nexora-io");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> plugin.getLogger().log(Level.WARNING, t.getName(), e));
            return thread;
        });
    }

    public CompletableFuture<List<Entry>> loadAll(List<String> kinds) {
        return CompletableFuture.supplyAsync(() -> {
            List<Entry> entries = new ArrayList<>();
            for (var kind : kinds) {
                var read = YamlConfiguration.loadConfiguration(file(kind));
                files.put(kind, YamlConfiguration.loadConfiguration(file(kind)));
                for (var id : read.getKeys(false)) {
                    var section = read.getConfigurationSection(id);
                    if (section != null) entries.add(new Entry(kind, id, section));
                }
            }
            return entries;
        }, io);
    }

    public void save(String kind, String id, Map<String, Object> data) {
        io.execute(() -> {
            var yaml = files.computeIfAbsent(kind, k -> YamlConfiguration.loadConfiguration(file(k)));
            yaml.set(id, null);
            yaml.createSection(id, data);
            write(kind, yaml);
        });
    }

    public void delete(String kind, String id) {
        io.execute(() -> {
            var yaml = files.computeIfAbsent(kind, k -> YamlConfiguration.loadConfiguration(file(k)));
            yaml.set(id, null);
            write(kind, yaml);
        });
    }

    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(plugin.getConfig().getLong("storage.shutdown-timeout-seconds", 10), TimeUnit.SECONDS)) io.shutdownNow();
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private File file(String kind) {
        return new File(plugin.getDataFolder(), kind + "s.yml");
    }

    private void write(String kind, YamlConfiguration yaml) {
        try {
            yaml.save(file(kind));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, kind, e);
        }
    }
}
