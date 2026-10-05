package net.spxry.nexora.object;

import net.spxry.nexora.Nexora;
import net.spxry.nexora.storage.ObjectStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class ObjectManager {
    private final Nexora plugin;
    private final Map<String, NexoraObject> objects = new ConcurrentHashMap<>();
    private final Map<Integer, NexoraObject> byEntity = new ConcurrentHashMap<>();
    private static final String DEMOS_FILE = "demos.yml";
    private volatile Pattern idPattern;
    private volatile YamlConfiguration demos = new YamlConfiguration();

    public ObjectManager(Nexora plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        idPattern = Pattern.compile(plugin.getConfig().getString("ids.pattern", "[a-zA-Z0-9_-]{1,32}"));
        var file = new File(plugin.getDataFolder(), DEMOS_FILE);
        if (!file.exists()) plugin.saveResource(DEMOS_FILE, false);
        demos = YamlConfiguration.loadConfiguration(file);
    }

    public Collection<NexoraObject> all() { return objects.values(); }

    public List<NexoraObject> list(String kind) {
        return objects.values().stream().filter(o -> o.kind().equals(kind)).sorted(Comparator.comparing(NexoraObject::id)).toList();
    }

    public Optional<NexoraObject> get(String kind, String id) {
        if (kind == null || id == null) return Optional.empty();
        return Optional.ofNullable(objects.get(key(kind, id)));
    }

    public Optional<NexoraObject> byEntityId(int entityId) { return Optional.ofNullable(byEntity.get(entityId)); }

    public boolean validId(String id) { return id != null && idPattern.matcher(id).matches(); }

    public boolean exists(String kind, String id) { return objects.containsKey(key(kind, id)); }

    public Map<String, String> defaults(String kind) {
        Map<String, String> out = new LinkedHashMap<>();
        var section = plugin.getConfig().getConfigurationSection("defaults." + kind);
        if (section != null) for (var key : section.getKeys(false)) out.put(key, String.valueOf(section.get(key)));
        return out;
    }

    public HoloLine newLine() {
        var line = new HoloLine();
        line.apply(plugin.schema(), defaults(NexoraObject.LINE));
        return line;
    }

    public List<String> spawnDemo(Player player) {
        var config = demos;
        var root = config.getConfigurationSection("objects");
        if (root == null) return List.of();
        clearDemo();
        var prefix = config.getString("id-prefix", "");
        var base = player.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        String skinValue = "", skinSignature = "";
        for (var property : player.getPlayerProfile().getProperties()) {
            if (!config.getString("skin-property", "").equals(property.getName())) continue;
            skinValue = property.getValue();
            skinSignature = property.getSignature() == null ? "" : property.getSignature();
        }
        List<String> created = new ArrayList<>();
        for (var name : root.getKeys(false)) {
            var section = root.getConfigurationSection(name);
            if (section == null) continue;
            var kind = section.getString("kind", NexoraObject.HOLOGRAM);
            double forward = section.getDouble("offset.forward"), right = section.getDouble("offset.right"), up = section.getDouble("offset.up");
            var location = base.clone().add(forwardX * forward + rightX * right, up, forwardZ * forward + rightZ * right);
            location.setYaw(base.getYaw() + section.getInt("offset.yaw"));
            location.setPitch(0);
            var id = prefix + name;
            NexoraObject object = NexoraObject.NPC.equals(kind) ? new Npc(plugin, id, location) : new Hologram(plugin, id, location);
            object.apply(defaults(object.kind()));
            if (object instanceof Hologram hologram) hologram.lines().add(newLine());
            applyTemplate(object, section);
            if (object instanceof Npc && section.getBoolean("copy-skin") && !skinValue.isEmpty()) {
                object.apply(Map.of("skin-name", "", "skin-value", skinValue, "skin-signature", skinSignature));
            }
            double radius = section.getDouble("path-radius");
            if (radius > 0) {
                for (var corner : new double[][]{{-radius, -radius}, {radius, -radius}, {radius, radius}, {-radius, radius}}) {
                    object.addWaypoint(location.clone().add(corner[0], 0, corner[1]));
                }
            }
            register(object);
            created.add(id);
        }
        return created;
    }

    public int clearDemo() {
        var prefix = demos.getString("id-prefix", "");
        if (prefix.isEmpty()) return 0;
        int count = 0;
        for (var object : List.copyOf(objects.values())) {
            if (!object.id().startsWith(prefix)) continue;
            delete(object);
            count++;
        }
        return count;
    }

    private void applyTemplate(NexoraObject object, ConfigurationSection section) {
        object.apply(strings(section.getConfigurationSection("props")));
        var items = section.getConfigurationSection("equipment");
        if (object instanceof Npc npc && items != null) {
            npc.equipment().clear();
            for (var slot : items.getKeys(false)) {
                var material = Material.matchMaterial(items.getString(slot, ""));
                if (material == null || !material.isItem() || material.isAir()) continue;
                try {
                    npc.equipment().put(EquipmentSlot.valueOf(slot.toUpperCase(Locale.ROOT)), ItemStack.of(material));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (object instanceof Hologram hologram && section.isList("lines")) {
            List<HoloLine> lines = new ArrayList<>();
            for (var map : section.getMapList("lines")) {
                Map<String, String> values = new LinkedHashMap<>();
                map.forEach((key, value) -> values.put(String.valueOf(key), String.valueOf(value)));
                var line = newLine();
                line.apply(plugin.schema(), values);
                lines.add(line);
            }
            hologram.lines().clear();
            hologram.lines().addAll(lines);
        }
    }

    private static Map<String, String> strings(ConfigurationSection section) {
        Map<String, String> out = new LinkedHashMap<>();
        if (section != null) for (var key : section.getKeys(false)) out.put(key, String.valueOf(section.get(key)));
        return out;
    }

    public Hologram createHologram(String id, Location location) {
        var hologram = new Hologram(plugin, id, location);
        hologram.apply(defaults(NexoraObject.HOLOGRAM));
        hologram.lines().add(newLine());
        register(hologram);
        return hologram;
    }

    public Npc createNpc(String id, Location location, String entityType) {
        var npc = new Npc(plugin, id, location);
        var values = defaults(NexoraObject.NPC);
        if (entityType != null) values.put("entity-type", entityType);
        npc.apply(values);
        register(npc);
        return npc;
    }

    private void register(NexoraObject object) {
        objects.put(key(object.kind(), object.id()), object);
        plugin.store().save(object.kind(), object.id(), object.snapshot());
        object.start();
    }

    public <T extends NexoraObject> CompletableFuture<Void> mutate(T object, Consumer<T> change) {
        var future = new CompletableFuture<Void>();
        plugin.scheduler().runAtLocation(object.anchor(), () -> {
            if (object.isRemoved()) {
                future.completeExceptionally(new IllegalStateException(object.id()));
                return;
            }
            try {
                change.accept(object);
                object.restart();
                plugin.store().save(object.kind(), object.id(), object.snapshot());
                future.complete(null);
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public <T extends NexoraObject, R> CompletableFuture<R> query(T object, Function<T, R> fn) {
        var future = new CompletableFuture<R>();
        plugin.scheduler().runAtLocation(object.anchor(), () -> {
            try {
                future.complete(fn.apply(object));
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void delete(NexoraObject object) {
        objects.remove(key(object.kind(), object.id()));
        object.remove();
        for (var id : object.entityIds()) byEntity.remove(id, object);
        plugin.store().delete(object.kind(), object.id());
    }

    void reindex(NexoraObject object, int[] oldIds, int[] newIds) {
        for (var id : oldIds) byEntity.remove(id, object);
        if (object.isRemoved()) return;
        for (var id : newIds) byEntity.put(id, object);
    }

    public void loadAll() {
        plugin.store().loadAll(List.of(NexoraObject.HOLOGRAM, NexoraObject.NPC))
            .thenAccept(entries -> plugin.scheduler().runGlobal(() -> entries.forEach(this::restore)))
            .exceptionally(e -> {
                plugin.getLogger().log(Level.WARNING, "load", e);
                return null;
            });
    }

    private void restore(ObjectStore.Entry entry) {
        var data = entry.data();
        var world = Bukkit.getWorld(data.getString("world", ""));
        if (world == null) return;
        var location = new Location(world, data.getDouble("x"), data.getDouble("y"), data.getDouble("z"), (float) data.getDouble("yaw"), 0);
        NexoraObject object = switch (entry.kind()) {
            case NexoraObject.HOLOGRAM -> new Hologram(plugin, entry.id(), location);
            case NexoraObject.NPC -> new Npc(plugin, entry.id(), location);
            default -> null;
        };
        if (object == null) return;
        try {
            object.restore(data);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, entry.kind() + ":" + entry.id(), e);
            return;
        }
        objects.put(key(object.kind(), object.id()), object);
        object.start();
    }

    public void restartAll() {
        for (var object : objects.values()) plugin.scheduler().runAtLocation(object.anchor(), object::restart);
    }

    public void shutdown() {
        for (var object : objects.values()) object.remove();
        objects.clear();
        byEntity.clear();
    }

    private static String key(String kind, String id) {
        return kind + ":" + id.toLowerCase(Locale.ROOT);
    }
}
