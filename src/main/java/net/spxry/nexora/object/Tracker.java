package net.spxry.nexora.object;

import net.spxry.nexora.Nexora;
import net.spxry.nexora.npc.HasRuntime;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Tracker implements Listener {
    private final Nexora plugin;
    private final Map<UUID, Object> tasks = new ConcurrentHashMap<>();

    public Tracker(Nexora plugin) {
        this.plugin = plugin;
    }

    public void startAll() {
        for (var player : Bukkit.getOnlinePlayers()) plugin.scheduler().runAtEntity(player, () -> start(player));
    }

    public void stopAll() {
        for (var uuid : tasks.keySet()) stop(uuid);
    }

    public void start(Player player) {
        var uuid = player.getUniqueId();
        int interval = Math.max(1, plugin.getConfig().getInt("tracker.interval-ticks", 5));
        var task = plugin.scheduler().runAtEntityTimer(player, () -> tick(player), () -> stop(uuid), 1, interval);
        if (task == null) return;
        var old = tasks.put(uuid, task);
        if (old != null) plugin.scheduler().cancel(old);
    }

    public void stop(UUID uuid) {
        var task = tasks.remove(uuid);
        if (task != null) plugin.scheduler().cancel(task);
        for (var object : plugin.objects().all()) object.forget(uuid);
    }

    public void reset(Player player) {
        for (var object : plugin.objects().all()) object.hide(player);
    }

    private void tick(Player player) {
        if (!player.isOnline()) return;
        var location = player.getLocation();
        var eye = player.getEyeLocation();
        var direction = eye.getDirection();
        var uuid = player.getUniqueId();
        for (var object : plugin.objects().all()) {
            var runtime = object instanceof HasRuntime has ? has : null;
            boolean inRange = !object.isRemoved() && object.inRange(location);
            boolean viewing = object.isViewer(uuid);
            if (inRange && !viewing) {
                object.show(player);
                if (runtime != null && object.isViewer(uuid)) runtime.runtime().shown(player);
            } else if (!inRange && viewing) {
                if (runtime != null) runtime.runtime().departed(player);
                object.hide(player);
            }
            if (!inRange) continue;
            if (object instanceof Npc npc) npc.look(player, eye);
            if (object instanceof Hologram hologram) hologram.viewerAt(uuid, eye);
            if (runtime != null && object.isViewer(uuid)) {
                runtime.runtime().observe(player, eye, direction, runtime.lookTriggerRange(), runtime.approachTriggerRange());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        double limit = plugin.getConfig().getDouble("tracker.reset-distance", 32);
        if (from.getWorld() != to.getWorld() || from.distanceSquared(to) > limit * limit) reset(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        reset(event.getPlayer());
    }
}
