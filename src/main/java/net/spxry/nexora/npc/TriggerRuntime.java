package net.spxry.nexora.npc;

import net.spxry.nexora.Nexora;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class TriggerRuntime {
    private record CooldownKey(int rule, UUID player) {}

    private static final UUID NO_PLAYER = new UUID(0L, 0L);
    private static final Map<String, Triggers.Event> EVENTS = events();
    private static final String LOOK_ANGLE_KEY = "look-angle";
    private static final String DEFAULT_COOLDOWN_KEY = "default-cooldown";
    private static final double DEFAULT_LOOK_ANGLE = 10.0;
    private static final int DEFAULT_COOLDOWN = 40;
    private static final double LOOK_AWAY_FACTOR = 1.5;
    private static final double RANGE_HYSTERESIS = 0.5;
    private static final double MIN_LENGTH = 1.0E-6;
    private static final long MILLIS_PER_TICK = 50L;
    private static final int PRUNE_PERIOD = 200;
    private static final int EYE_SIZE = 3;

    private final Nexora plugin;
    private final TriggerHost host;
    private final Effects effects;
    private final Map<CooldownKey, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> gazing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> near = ConcurrentHashMap.newKeySet();
    private final Map<UUID, double[]> eyes = new ConcurrentHashMap<>();
    private volatile Map<Triggers.Event, List<Triggers.Rule>> rules = Map.of();
    private volatile List<Triggers.Rule> intervals = List.of();
    private volatile List<Triggers.Rule> holdings = List.of();
    private volatile Set<String> signals = Set.of();
    private volatile double lookCos;
    private volatile double lookAwayCos;
    private long ticks;

    public TriggerRuntime(Nexora plugin, TriggerHost host) {
        this.plugin = plugin;
        this.host = host;
        this.effects = new Effects(plugin, host, this);
    }

    private static Map<String, Triggers.Event> events() {
        Map<String, Triggers.Event> map = new HashMap<>();
        for (var event : Triggers.Event.values()) map.put(event.name().toLowerCase(Locale.ROOT), event);
        return Map.copyOf(map);
    }

    public void rules(String text) {
        var parsed = Triggers.parse(plugin, text);
        Set<String> names = new HashSet<>();
        for (var rule : parsed.getOrDefault(Triggers.Event.SIGNAL, List.of())) names.add(rule.name());
        rules = parsed;
        intervals = parsed.getOrDefault(Triggers.Event.INTERVAL, List.of());
        holdings = parsed.getOrDefault(Triggers.Event.HOLDING, List.of());
        signals = Set.copyOf(names);
        cooldowns.clear();
        ticks = 0;
        double angle = plugin.getConfig().getDouble(Triggers.key(plugin, LOOK_ANGLE_KEY), DEFAULT_LOOK_ANGLE);
        lookCos = Math.cos(Math.toRadians(angle));
        lookAwayCos = Math.cos(Math.toRadians(angle * LOOK_AWAY_FACTOR));
    }

    public boolean listens(String signal) {
        return signals.contains(signal);
    }

    public double[] eye(UUID uuid) {
        return eyes.get(uuid);
    }

    public void click(Player player, boolean attack) {
        if (rules.isEmpty()) return;
        var eye = player.getEyeLocation();
        remember(player.getUniqueId(), eye.getX(), eye.getY(), eye.getZ());
        fire(Triggers.Event.CLICK, player, false, 0, null);
        fire(attack ? Triggers.Event.LEFTCLICK : Triggers.Event.RIGHTCLICK, player, false, 0, null);
    }

    public void shown(Player player) {
        if (rules.isEmpty()) return;
        var eye = player.getEyeLocation();
        remember(player.getUniqueId(), eye.getX(), eye.getY(), eye.getZ());
        fire(Triggers.Event.SPAWN, player, false, 0, null);
    }

    public void departed(Player player) {
        var uuid = player.getUniqueId();
        if (near.remove(uuid)) fire(Triggers.Event.LEAVE, player, false, 0, null);
        if (gazing.remove(uuid)) fire(Triggers.Event.LOOKAWAY, player, false, 0, null);
        eyes.remove(uuid);
    }

    public void forget(UUID uuid) {
        near.remove(uuid);
        gazing.remove(uuid);
        eyes.remove(uuid);
    }

    public void observe(Player player, Location eye, Vector direction, double lookRange, double approachRange) {
        var table = rules;
        if (table.isEmpty()) return;
        var uuid = player.getUniqueId();
        remember(uuid, eye.getX(), eye.getY(), eye.getZ());
        boolean wantsNear = table.containsKey(Triggers.Event.APPROACH) || table.containsKey(Triggers.Event.LEAVE);
        boolean wantsLook = table.containsKey(Triggers.Event.LOOK) || table.containsKey(Triggers.Event.LOOKAWAY);
        if (!wantsNear && !wantsLook) return;
        var centre = point(Triggers.ANCHOR_CENTER);
        double dx = centre.getX() - eye.getX();
        double dy = centre.getY() - eye.getY();
        double dz = centre.getZ() - eye.getZ();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (wantsNear) {
            boolean now = within(distanceSquared, approachRange, near.contains(uuid));
            if (now && near.add(uuid)) fire(Triggers.Event.APPROACH, player, false, 0, null);
            else if (!now && near.remove(uuid)) fire(Triggers.Event.LEAVE, player, false, 0, null);
        }
        if (wantsLook) {
            boolean was = gazing.contains(uuid);
            boolean now = within(distanceSquared, lookRange, was) && aimed(eye, direction, was ? lookAwayCos : lookCos, centre);
            if (now && gazing.add(uuid)) fire(Triggers.Event.LOOK, player, false, 0, null);
            else if (!now && gazing.remove(uuid)) fire(Triggers.Event.LOOKAWAY, player, false, 0, null);
        }
    }

    public void fire(String event, Player player, int value) {
        var parsed = EVENTS.get(event);
        if (parsed == null) parsed = EVENTS.get(event.toLowerCase(Locale.ROOT));
        if (parsed != null) fire(parsed, player, true, value, null);
    }

    public void fire(Triggers.Event event, Player player, int value) {
        fire(event, player, true, value, null);
    }

    public void signal(String name, Player source) {
        signal(name, source, null);
    }

    void signal(String raw, Player source, SignalBus.Chain chain) {
        var name = Triggers.signalName(raw);
        var list = rules.get(Triggers.Event.SIGNAL);
        if (name == null || list == null) return;
        var active = chain != null ? chain : new SignalBus.Chain(name, null, 1, false);
        for (int i = 0; i < list.size(); i++) {
            var rule = list.get(i);
            if (rule.name().equals(name)) run(rule, source, true, active);
        }
    }

    public void tick(int interval, Predicate<Material> holding) {
        ticks += interval;
        var timed = intervals;
        for (int i = 0; i < timed.size(); i++) {
            var rule = timed.get(i);
            if (crossed(rule.every(), interval)) run(rule, null, true, null);
        }
        var held = holdings;
        for (int i = 0; i < held.size(); i++) {
            var rule = held.get(i);
            if (holding != null && crossed(rule.every(), interval) && holding.test(rule.material())) run(rule, null, true, null);
        }
        if (crossed(PRUNE_PERIOD, interval)) prune();
    }

    private void fire(Triggers.Event event, Player player, boolean region, int value, SignalBus.Chain chain) {
        var list = rules.get(event);
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            var rule = list.get(i);
            if (rule.accepts(value)) run(rule, player, region, chain);
        }
    }

    private void run(Triggers.Rule rule, Player player, boolean region, SignalBus.Chain chain) {
        if (!ready(rule, player)) return;
        effects.run(rule.actions(), player, region, chain);
    }

    private boolean ready(Triggers.Rule rule, Player player) {
        int cooldownTicks = rule.cooldown() != Triggers.UNSET_COOLDOWN ? rule.cooldown()
            : rule.event().playerScoped() ? plugin.getConfig().getInt(Triggers.key(plugin, DEFAULT_COOLDOWN_KEY), DEFAULT_COOLDOWN) : 0;
        if (cooldownTicks <= 0) return true;
        var key = new CooldownKey(rule.index(), player == null ? NO_PLAYER : player.getUniqueId());
        long now = System.currentTimeMillis();
        var until = cooldowns.get(key);
        if (until != null && until > now) return false;
        cooldowns.put(key, now + cooldownTicks * MILLIS_PER_TICK);
        return true;
    }

    private void prune() {
        long now = System.currentTimeMillis();
        cooldowns.values().removeIf(until -> until <= now);
    }

    private boolean crossed(int period, int interval) {
        return period > 0 && ticks / period != (ticks - interval) / period;
    }

    private void remember(UUID uuid, double x, double y, double z) {
        var eye = eyes.computeIfAbsent(uuid, key -> new double[EYE_SIZE]);
        eye[0] = x;
        eye[1] = y;
        eye[2] = z;
    }

    private Vector point(String anchor) {
        var found = host.anchorPoint(anchor);
        return found != null ? found : new Vector(host.x(), host.y(), host.z());
    }

    private static boolean within(double distanceSquared, double range, boolean already) {
        double limit = already ? range + RANGE_HYSTERESIS : range;
        return distanceSquared <= limit * limit;
    }

    private boolean aimed(Location eye, Vector direction, double threshold, Vector centre) {
        if (aimedAt(eye, direction, centre, threshold)) return true;
        var head = host.anchorPoint(Triggers.ANCHOR_HEAD);
        return head != null && aimedAt(eye, direction, head, threshold);
    }

    private static boolean aimedAt(Location eye, Vector direction, Vector point, double threshold) {
        double dx = point.getX() - eye.getX();
        double dy = point.getY() - eye.getY();
        double dz = point.getZ() - eye.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < MIN_LENGTH) return true;
        return (direction.getX() * dx + direction.getY() * dy + direction.getZ() * dz) / length >= threshold;
    }
}
