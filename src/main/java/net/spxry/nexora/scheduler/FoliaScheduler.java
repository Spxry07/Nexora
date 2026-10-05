package net.spxry.nexora.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.TimeUnit;

public class FoliaScheduler {

    private static final long TICK_MS = 50L;

    private final JavaPlugin plugin;
    private final boolean folia;

    public FoliaScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        boolean detected;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            detected = true;
        } catch (ClassNotFoundException e) {
            detected = false;
        }
        this.folia = detected;
    }

    public boolean isFolia() {
        return folia;
    }

    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, $ -> task.run());
    }

    public void runAsyncDelayed(Runnable task, long delayTicks) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, $ -> task.run(), Math.max(1L, delayTicks) * TICK_MS, TimeUnit.MILLISECONDS);
    }

    public Object runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        var scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, $ -> task.run(),
            Math.max(1L, delayTicks) * TICK_MS, Math.max(1L, periodTicks) * TICK_MS, TimeUnit.MILLISECONDS);
        return (Runnable) scheduled::cancel;
    }

    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, $ -> task.run());
    }

    public void runGlobalDelayed(Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, $ -> task.run(), Math.max(1L, delayTicks));
    }

    public Object runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        var scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, $ -> task.run(),
            Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return (Runnable) scheduled::cancel;
    }

    public void runAtEntity(Entity entity, Runnable task) {
        runAtEntity(entity, task, null);
    }

    public void runAtEntity(Entity entity, Runnable task, Runnable retired) {
        entity.getScheduler().run(plugin, $ -> task.run(), retired);
    }

    public void runAtEntityDelayed(Entity entity, Runnable task, long delayTicks) {
        runAtEntityDelayed(entity, task, null, delayTicks);
    }

    public void runAtEntityDelayed(Entity entity, Runnable task, Runnable retired, long delayTicks) {
        entity.getScheduler().runDelayed(plugin, $ -> task.run(), retired, Math.max(1L, delayTicks));
    }

    public Object runAtEntityTimer(Entity entity, Runnable task, Runnable retired, long delayTicks, long periodTicks) {
        var scheduled = entity.getScheduler().runAtFixedRate(plugin, $ -> task.run(), retired,
            Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return scheduled == null ? null : (Runnable) scheduled::cancel;
    }

    public void runAtLocation(Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, $ -> task.run());
    }

    public void runAtLocationDelayed(Location location, Runnable task, long delayTicks) {
        Bukkit.getRegionScheduler().runDelayed(plugin, location, $ -> task.run(), Math.max(1L, delayTicks));
    }

    public Object runAtLocationTimer(Location location, Runnable task, long delayTicks, long periodTicks) {
        var scheduled = Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, $ -> task.run(),
            Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return (Runnable) scheduled::cancel;
    }

    public void runAtChunk(World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, $ -> task.run());
    }

    public void cancel(Object task) {
        if (task instanceof Runnable r) r.run();
    }
}
