package net.spxry.nexora.object;

import net.minecraft.network.protocol.Packet;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.edit.Binding;
import net.spxry.nexora.nms.Packets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public abstract class NexoraObject {
    public static final String HOLOGRAM = "hologram";
    public static final String NPC = "npc";
    public static final String LINE = "line";

    protected record Built(List<Packet<?>> spawn, List<Packet<?>> despawn, int[] ids) {}

    protected final Nexora plugin;
    private final String id;
    private volatile Location anchor;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> joined = ConcurrentHashMap.newKeySet();
    private Particle particleType;
    private String particleName;
    private volatile Packet<?> spawnBundle;
    private volatile Packet<?> despawnBundle;
    private volatile int[] entityIds = new int[0];
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile boolean removed;
    private volatile Object task;
    private int particleCooldown;
    protected long ticks;

    final List<Vector> waypoints = new CopyOnWriteArrayList<>();
    volatile double viewRange;
    double pathSpeed;
    String particle = "";
    int particleCount;
    double particleRadius;
    int particleInterval;

    protected NexoraObject(Nexora plugin, String id, Location anchor) {
        this.plugin = plugin;
        this.id = id;
        this.anchor = anchor.clone();
        setPosition(anchor.getX(), anchor.getY(), anchor.getZ());
    }

    public abstract String kind();

    public abstract Map<String, String> values();

    public abstract List<String> apply(Map<String, String> values);

    protected abstract Built build(Location anchor);

    protected abstract void tick(int interval);

    protected void writeExtra(Map<String, Object> out) {}

    protected void readExtra(ConfigurationSection section) {}

    protected void onHide(UUID viewer) {}

    protected List<Packet<?>> catchUp() { return List.of(); }

    static <T extends NexoraObject> Map<String, Binding<T>> commonBindings() {
        Map<String, Binding<T>> map = new LinkedHashMap<>();
        map.put("view-range", Binding.number(o -> o.viewRange, (o, v) -> o.viewRange = v));
        map.put("path-speed", Binding.number(o -> o.pathSpeed, (o, v) -> o.pathSpeed = v));
        map.put("particle", Binding.text(o -> o.particle, (o, v) -> o.particle = v));
        map.put("particle-count", Binding.integer(o -> o.particleCount, (o, v) -> o.particleCount = v));
        map.put("particle-radius", Binding.number(o -> o.particleRadius, (o, v) -> o.particleRadius = v));
        map.put("particle-interval", Binding.integer(o -> o.particleInterval, (o, v) -> o.particleInterval = v));
        return map;
    }

    public final String id() { return id; }

    public final Location anchor() { return anchor.clone(); }

    public final double x() { return x; }

    public final double y() { return y; }

    public final double z() { return z; }

    public final double viewRange() { return viewRange; }

    public final boolean isRemoved() { return removed; }

    public final int[] entityIds() { return entityIds.clone(); }

    public final int waypointCount() { return waypoints.size(); }

    public final void moveTo(Location location) {
        anchor = location.clone();
        setPosition(location.getX(), location.getY(), location.getZ());
    }

    public final void addWaypoint(Location location) {
        if (waypoints.size() < plugin.getConfig().getInt("limits.max-waypoints", 64)) waypoints.add(location.toVector());
    }

    public final void clearWaypoints() { waypoints.clear(); }

    public final boolean inRange(Location location) {
        var world = anchor.getWorld();
        if (world == null || location.getWorld() == null || !world.getUID().equals(location.getWorld().getUID())) return false;
        double dx = location.getX() - x, dy = location.getY() - y, dz = location.getZ() - z;
        return dx * dx + dy * dy + dz * dz <= viewRange * viewRange;
    }

    public final boolean isViewer(UUID uuid) { return viewers.contains(uuid); }

    public final void show(Player player) {
        synchronized (this) {
            if (removed || spawnBundle == null) return;
            if (viewers.add(player.getUniqueId())) {
                Packets.send(player, spawnBundle);
                joined.add(player.getUniqueId());
            }
        }
    }

    public final void hide(Player player) {
        synchronized (this) {
            if (viewers.remove(player.getUniqueId()) && despawnBundle != null) Packets.send(player, despawnBundle);
        }
        onHide(player.getUniqueId());
    }

    public final void forget(UUID uuid) {
        viewers.remove(uuid);
        onHide(uuid);
    }

    public final void start() {
        if (removed) return;
        var location = anchor.clone();
        int interval = Math.max(1, plugin.getConfig().getInt("animation.interval-ticks", 2));
        plugin.scheduler().runAtLocation(location, () -> {
            if (removed) return;
            Built built;
            try {
                built = build(location);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, kind() + ":" + id, e);
                return;
            }
            swap(built);
            task = plugin.scheduler().runAtLocationTimer(location, () -> safeTick(interval), interval, interval);
        });
    }

    public final void restart() {
        cancelTask();
        ticks = 0;
        start();
    }

    public final void remove() {
        removed = true;
        cancelTask();
        synchronized (this) {
            for (var uuid : viewers) {
                var player = Bukkit.getPlayer(uuid);
                if (player != null) Packets.send(player, despawnBundle);
            }
            viewers.clear();
        }
    }

    public final Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        var a = anchor;
        out.put("world", a.getWorld() == null ? "" : a.getWorld().getName());
        out.put("x", a.getX());
        out.put("y", a.getY());
        out.put("z", a.getZ());
        out.put("yaw", (double) a.getYaw());
        out.put("props", new LinkedHashMap<>(values()));
        List<String> points = new ArrayList<>();
        for (var v : waypoints) points.add(v.getX() + "," + v.getY() + "," + v.getZ());
        out.put("waypoints", points);
        writeExtra(out);
        return out;
    }

    public final void restore(ConfigurationSection section) {
        var props = section.getConfigurationSection("props");
        if (props != null) {
            Map<String, String> values = new LinkedHashMap<>();
            for (var key : props.getKeys(false)) values.put(key, String.valueOf(props.get(key)));
            apply(values);
        }
        waypoints.clear();
        for (var raw : section.getStringList("waypoints")) {
            var parts = raw.split(",");
            if (parts.length != 3) continue;
            try {
                waypoints.add(new Vector(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        readExtra(section);
    }

    private void safeTick(int interval) {
        if (removed) return;
        try {
            tick(interval);
            if (!joined.isEmpty()) sendCatchUp();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, kind() + ":" + id, e);
            cancelTask();
        }
    }

    private void cancelTask() {
        var current = task;
        task = null;
        if (current != null) plugin.scheduler().cancel(current);
    }

    private void swap(Built built) {
        var spawn = Packets.bundle(built.spawn());
        var despawn = Packets.bundle(built.despawn());
        synchronized (this) {
            var oldDespawn = despawnBundle;
            var oldIds = entityIds;
            spawnBundle = spawn;
            despawnBundle = despawn;
            entityIds = built.ids();
            for (var uuid : viewers) {
                var player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    viewers.remove(uuid);
                    continue;
                }
                Packets.send(player, oldDespawn);
                Packets.send(player, spawn);
            }
            plugin.objects().reindex(this, oldIds, built.ids());
        }
    }

    private void sendCatchUp() {
        var packets = catchUp();
        var bundle = packets.isEmpty() ? null : Packets.bundle(packets);
        for (var uuid : List.copyOf(joined)) {
            joined.remove(uuid);
            if (bundle == null || !viewers.contains(uuid)) continue;
            var player = Bukkit.getPlayer(uuid);
            if (player != null) Packets.send(player, bundle);
        }
    }

    protected final void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    protected final Set<UUID> viewers() { return viewers; }

    protected final void broadcast(List<Packet<?>> packets) {
        broadcast(packets, Set.of());
    }

    protected final void broadcast(List<Packet<?>> packets, Set<UUID> except) {
        if (packets.isEmpty() || viewers.isEmpty()) return;
        var packet = packets.size() == 1 ? packets.get(0) : Packets.bundle(packets);
        for (var uuid : viewers) {
            if (except.contains(uuid)) continue;
            var player = Bukkit.getPlayer(uuid);
            if (player != null) Packets.send(player, packet);
        }
    }

    protected final double[] pathPoint(double seconds) {
        if (pathSpeed <= 0 || waypoints.size() < 2) return null;
        var points = List.copyOf(waypoints);
        int n = points.size();
        double[] lengths = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            lengths[i] = points.get(i).distance(points.get((i + 1) % n));
            total += lengths[i];
        }
        if (total <= 0) return null;
        double d = (seconds * pathSpeed) % total;
        for (int i = 0; i < n; i++) {
            if (d <= lengths[i] || i == n - 1) {
                var a = points.get(i);
                var b = points.get((i + 1) % n);
                double f = lengths[i] == 0 ? 0 : Math.min(1, d / lengths[i]);
                double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                return new double[]{a.getX() + dx * f, a.getY() + (b.getY() - a.getY()) * f, a.getZ() + dz * f, yaw};
            }
            d -= lengths[i];
        }
        return null;
    }

    protected final void particles(double cx, double cy, double cz, int interval) {
        if (particleInterval <= 0 || particleCount <= 0 || particle.isEmpty() || viewers.isEmpty()) return;
        particleCooldown -= interval;
        if (particleCooldown > 0) return;
        particleCooldown = particleInterval;
        if (!particle.equals(particleName)) {
            particleName = particle;
            try {
                var parsed = Particle.valueOf(particle.toUpperCase(Locale.ROOT));
                particleType = parsed.getDataType() == Void.class ? parsed : null;
            } catch (IllegalArgumentException e) {
                particleType = null;
            }
        }
        var type = particleType;
        if (type == null) return;
        var random = ThreadLocalRandom.current();
        double r = particleRadius;
        for (var uuid : viewers) {
            var player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.spawnParticle(type, cx + (random.nextDouble() * 2 - 1) * r, cy, cz + (random.nextDouble() * 2 - 1) * r, particleCount, r / 2, r / 2, r / 2, 0);
        }
    }
}
