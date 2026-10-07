package net.spxry.nexora.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.config.MessageManager;
import net.spxry.nexora.object.NexoraObject;
import net.spxry.nexora.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class Effects {
    private static final String FADE_IN_KEY = "title.fade-in";
    private static final String STAY_KEY = "title.stay";
    private static final String FADE_OUT_KEY = "title.fade-out";
    private static final long DEFAULT_FADE_IN = 500L;
    private static final long DEFAULT_STAY = 3500L;
    private static final long DEFAULT_FADE_OUT = 1000L;
    private static final String PH_PLAYER = "player";
    private static final String PH_NPC = "npc";
    private static final String PH_HOLO = "holo";
    private static final String PH_ID = "id";
    private static final String PH_SIGNAL = "signal";
    private static final String PH_UUID = "uuid";
    private static final String PH_PREFIX = "prefix";
    private static final double CHEST_DROP = 0.4;
    private static final double RING_VARIANCE = 0.5;
    private static final double FULL_TURN = Math.PI * 2;
    private static final double DEFAULT_HEIGHT = 1.0;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private final Nexora plugin;
    private final TriggerHost host;
    private final TriggerRuntime runtime;

    public Effects(Nexora plugin, TriggerHost host, TriggerRuntime runtime) {
        this.plugin = plugin;
        this.host = host;
        this.runtime = runtime;
    }

    public void run(List<Triggers.Action> actions, Player player, boolean region, SignalBus.Chain chain) {
        run(actions, 0, player, region, chain);
    }

    private void run(List<Triggers.Action> actions, int from, Player player, boolean region, SignalBus.Chain chain) {
        Map<String, String> placeholders = null;
        for (int i = from; i < actions.size(); i++) {
            var action = actions.get(i);
            if (action instanceof Triggers.DelayAction delay) {
                resume(actions, i + 1, player, delay.ticks(), chain);
                return;
            }
            if (placeholders == null) placeholders = placeholders(player, chain);
            try {
                execute(action, player, region, chain, placeholders);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, host.id(), e);
            }
        }
    }

    private void resume(List<Triggers.Action> actions, int next, Player player, int ticks, SignalBus.Chain chain) {
        var fresh = chain == null ? null : chain.detach();
        plugin.scheduler().runAtLocationDelayed(host.anchor(), () -> {
            if (player != null && !player.isOnline()) return;
            run(actions, next, player, true, fresh);
        }, ticks);
    }

    private Map<String, String> placeholders(Player player, SignalBus.Chain chain) {
        Map<String, String> values = new HashMap<>();
        values.put(NexoraObject.NPC.equals(host.kind()) ? PH_NPC : PH_HOLO, host.id());
        values.put(PH_ID, host.id());
        values.put(PH_PREFIX, plugin.messages().getPrefix());
        if (chain != null) values.put(PH_SIGNAL, chain.name());
        if (player != null) {
            values.put(PH_PLAYER, player.getName());
            values.put(PH_UUID, player.getUniqueId().toString());
        }
        return values;
    }

    private void execute(Triggers.Action action, Player player, boolean region, SignalBus.Chain chain, Map<String, String> placeholders) {
        switch (action) {
            case Triggers.ParticleAction particle -> particle(particle, player);
            case Triggers.SoundAction sound -> sound(sound, player);
            case Triggers.TextAction text -> text(text, player, region, placeholders);
            case Triggers.CommandAction command -> command(command, player, placeholders);
            case Triggers.SignalAction signal -> plugin.signals().emit(signal.name(), player, chain);
            case Triggers.HostAction call -> hostAction(call, player, region);
            case Triggers.DelayAction delay -> {
            }
        }
    }

    private void hostAction(Triggers.HostAction action, Player player, boolean region) {
        Runnable task = () -> {
            try {
                host.hostAction(action.verb(), action.args(), player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, host.id(), e);
            }
        };
        if (region) task.run();
        else plugin.scheduler().runAtLocation(host.anchor(), task);
    }

    private List<Player> viewerList() {
        List<Player> players = new ArrayList<>();
        for (var uuid : host.viewers()) {
            var player = Bukkit.getPlayer(uuid);
            if (player != null) players.add(player);
        }
        return players;
    }

    private List<Player> targets(Player player, boolean all) {
        if (player != null && !all) return List.of(player);
        return viewerList();
    }

    private void each(Player origin, boolean region, List<Player> targets, Consumer<Player> task) {
        for (var target : targets) {
            if (!region && target.equals(origin)) task.accept(target);
            else plugin.scheduler().runAtEntity(target, () -> task.accept(target));
        }
    }

    private void sound(Triggers.SoundAction action, Player player) {
        double x = host.x();
        double y = host.y();
        double z = host.z();
        for (var target : targets(player, action.all())) target.playSound(action.sound(), x, y, z);
    }

    private void text(Triggers.TextAction action, Player player, boolean region, Map<String, String> placeholders) {
        var main = MessageManager.apply(action.text(), placeholders);
        var sub = MessageManager.apply(action.sub(), placeholders);
        var targets = targets(player, false);
        switch (action.kind()) {
            case MESSAGE -> {
                var component = ColorUtil.colorize(main);
                each(player, region, targets, target -> target.sendMessage(component));
            }
            case ACTIONBAR -> {
                var component = ColorUtil.colorize(main);
                each(player, region, targets, target -> target.sendActionBar(component));
            }
            case TITLE -> {
                var title = Title.title(component(main), component(sub), times());
                each(player, region, targets, target -> target.showTitle(title));
            }
        }
    }

    private Component component(String text) {
        return text.isEmpty() ? Component.empty() : ColorUtil.colorize(text);
    }

    private Title.Times times() {
        var config = plugin.getConfig();
        return Title.Times.times(
            Duration.ofMillis(config.getLong(Triggers.key(plugin, FADE_IN_KEY), DEFAULT_FADE_IN)),
            Duration.ofMillis(config.getLong(Triggers.key(plugin, STAY_KEY), DEFAULT_STAY)),
            Duration.ofMillis(config.getLong(Triggers.key(plugin, FADE_OUT_KEY), DEFAULT_FADE_OUT)));
    }

    private void command(Triggers.CommandAction action, Player player, Map<String, String> placeholders) {
        if (action.console()) plugin.actions().runConsole(action.command(), placeholders);
        else if (player != null) plugin.actions().runPlayer(player, action.command(), placeholders);
    }

    private void particle(Triggers.ParticleAction action, Player player) {
        var targets = targets(player, action.all());
        if (targets.isEmpty()) return;
        var start = start(action, player);
        if (action.to() != null) {
            var end = resolve(action.to(), player);
            if (end != null) beam(targets, action, start, end);
            return;
        }
        if (isAround(action.at())) {
            for (int i = 0; i < action.count(); i++) emit(targets, action, ring(start, action.spread()), 1, 0);
            return;
        }
        emit(targets, action, start, action.count(), action.spread());
    }

    private static boolean isAround(Triggers.Target target) {
        return target.kind() == Triggers.TargetKind.ANCHOR && Triggers.ANCHOR_AROUND.equals(target.anchor());
    }

    private double[] start(Triggers.ParticleAction action, Player player) {
        var point = resolve(action.at(), player);
        double[] origin = point == null ? new double[]{host.x(), host.y(), host.z()} : new double[]{point.getX(), point.getY(), point.getZ()};
        var offset = action.offset();
        return new double[]{origin[X] + offset[X], origin[Y] + offset[Y], origin[Z] + offset[Z]};
    }

    private void beam(List<Player> targets, Triggers.ParticleAction action, double[] start, Vector end) {
        int count = action.count();
        for (int i = 0; i < count; i++) {
            double f = count == 1 ? 0 : i / (double) (count - 1);
            double[] at = {start[X] + (end.getX() - start[X]) * f, start[Y] + (end.getY() - start[Y]) * f, start[Z] + (end.getZ() - start[Z]) * f};
            emit(targets, action, at, 1, action.spread());
        }
    }

    private void emit(List<Player> targets, Triggers.ParticleAction action, double[] at, int count, double offset) {
        for (var target : targets) {
            target.spawnParticle(action.type(), at[X], at[Y], at[Z], count, offset, offset, offset, action.speed(), action.data());
        }
    }

    private double[] ring(double[] centre, double radius) {
        var random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * FULL_TURN;
        double lift = (random.nextDouble() - RING_VARIANCE) * height() * RING_VARIANCE;
        return new double[]{centre[X] + Math.cos(angle) * radius, centre[Y] + lift, centre[Z] + Math.sin(angle) * radius};
    }

    private double height() {
        var head = host.anchorPoint(Triggers.ANCHOR_HEAD);
        return head == null ? DEFAULT_HEIGHT : Math.max(0, head.getY() - host.y());
    }

    private Vector resolve(Triggers.Target target, Player player) {
        return switch (target.kind()) {
            case ANCHOR -> self(target.anchor());
            case PLAYER -> playerPoint(player);
            case NPC -> other(NexoraObject.NPC, target);
            case HOLO -> other(NexoraObject.HOLOGRAM, target);
            case RELATIVE -> new Vector(host.x() + target.vec()[X], host.y() + target.vec()[Y], host.z() + target.vec()[Z]);
            case ABSOLUTE -> new Vector(target.vec()[X], target.vec()[Y], target.vec()[Z]);
        };
    }

    private Vector self(String anchor) {
        return anchorOf(host, Triggers.ANCHOR_AROUND.equals(anchor) ? Triggers.ANCHOR_BODY : anchor);
    }

    private Vector other(String kind, Triggers.Target target) {
        var found = plugin.objects().get(kind, target.id());
        if (found.isEmpty() || !(found.get() instanceof TriggerHost other)) return null;
        return anchorOf(other, target.anchor());
    }

    private static Vector anchorOf(TriggerHost owner, String anchor) {
        var point = owner.anchorPoint(anchor);
        return point != null ? point : owner.anchorPoint(Triggers.ANCHOR_CENTER);
    }

    private Vector playerPoint(Player player) {
        if (player == null) return null;
        var eye = runtime.eye(player.getUniqueId());
        return eye == null ? null : new Vector(eye[X], eye[Y] - CHEST_DROP, eye[Z]);
    }
}
