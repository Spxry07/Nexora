package net.spxry.nexora.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.object.Npc;
import net.spxry.nexora.config.MessageManager;
import net.spxry.nexora.util.ColorUtil;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class Effects {
    private static final String FADE_IN_KEY = "npc.triggers.title.fade-in";
    private static final String STAY_KEY = "npc.triggers.title.stay";
    private static final String FADE_OUT_KEY = "npc.triggers.title.fade-out";
    private static final long DEFAULT_FADE_IN = 500L;
    private static final long DEFAULT_STAY = 3500L;
    private static final long DEFAULT_FADE_OUT = 1000L;
    private static final String PH_PLAYER = "player";
    private static final String PH_NPC = "npc";
    private static final String PH_UUID = "uuid";
    private static final String PH_PREFIX = "prefix";
    private static final double FEET_LIFT = 0.1;
    private static final double BODY_FACTOR = 0.6;
    private static final double HAND_FACTOR = 0.75;
    private static final double HAND_SIDE = 0.35;
    private static final double HAND_FORWARD = 0.2;
    private static final double ABOVE_LIFT = 0.5;
    private static final double RING_VARIANCE = 0.5;
    private static final double FULL_TURN = Math.PI * 2;

    private final Nexora plugin;
    private final Npc npc;

    public Effects(Nexora plugin, Npc npc) {
        this.plugin = plugin;
        this.npc = npc;
    }

    public void run(List<Triggers.Action> actions, Player player, boolean region) {
        var placeholders = placeholders(player);
        for (var action : actions) {
            try {
                execute(action, player, region, placeholders);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, npc.id(), e);
            }
        }
    }

    private Map<String, String> placeholders(Player player) {
        Map<String, String> values = new HashMap<>();
        values.put(PH_NPC, npc.id());
        values.put(PH_PREFIX, plugin.messages().getPrefix());
        if (player != null) {
            values.put(PH_PLAYER, player.getName());
            values.put(PH_UUID, player.getUniqueId().toString());
        }
        return values;
    }

    private void execute(Triggers.Action action, Player player, boolean region, Map<String, String> placeholders) {
        switch (action) {
            case Triggers.ParticleAction particle -> particle(particle, player);
            case Triggers.SoundAction sound -> sound(sound, player);
            case Triggers.TextAction text -> text(text, player, placeholders);
            case Triggers.CommandAction command -> command(command, player, placeholders);
            case Triggers.SwingAction swing -> onNpc(region, () -> npc.swing(swing.off()));
            case Triggers.GlowAction glow -> onNpc(region, () -> npc.glow(glow.mode()));
            case Triggers.AnimateAction animate -> onNpc(region, () -> npc.playback(animate.mode()));
            case Triggers.ItemAction item -> onNpc(region, () -> npc.overrideItem(item.material()));
            case Triggers.HurtAction hurt -> onNpc(region, npc::hurt);
            case Triggers.LookAction look -> {
                if (player != null) npc.faceToward(player);
            }
        }
    }

    private void onNpc(boolean region, Runnable task) {
        if (region) task.run();
        else plugin.scheduler().runAtLocation(npc.anchor(), task);
    }

    private List<Player> targets(Player player, boolean all) {
        if (player != null && !all) return List.of(player);
        return npc.viewerList();
    }

    private void each(Player origin, List<Player> targets, Consumer<Player> task) {
        for (var target : targets) {
            if (target.equals(origin)) task.accept(target);
            else plugin.scheduler().runAtEntity(target, () -> task.accept(target));
        }
    }

    private void sound(Triggers.SoundAction action, Player player) {
        double x = npc.x();
        double y = npc.y();
        double z = npc.z();
        for (var target : targets(player, action.all())) target.playSound(action.sound(), x, y, z);
    }

    private void text(Triggers.TextAction action, Player player, Map<String, String> placeholders) {
        var main = MessageManager.apply(action.text(), placeholders);
        var sub = MessageManager.apply(action.sub(), placeholders);
        var targets = targets(player, false);
        switch (action.kind()) {
            case MESSAGE -> {
                var component = ColorUtil.colorize(main);
                each(player, targets, target -> target.sendMessage(component));
            }
            case ACTIONBAR -> {
                var component = ColorUtil.colorize(main);
                each(player, targets, target -> target.sendActionBar(component));
            }
            case TITLE -> {
                var title = Title.title(component(main), component(sub), times());
                each(player, targets, target -> target.showTitle(title));
            }
        }
    }

    private Component component(String text) {
        return text.isEmpty() ? Component.empty() : ColorUtil.colorize(text);
    }

    private Title.Times times() {
        var config = plugin.getConfig();
        return Title.Times.times(
            Duration.ofMillis(config.getLong(FADE_IN_KEY, DEFAULT_FADE_IN)),
            Duration.ofMillis(config.getLong(STAY_KEY, DEFAULT_STAY)),
            Duration.ofMillis(config.getLong(FADE_OUT_KEY, DEFAULT_FADE_OUT)));
    }

    private void command(Triggers.CommandAction action, Player player, Map<String, String> placeholders) {
        if (action.console()) plugin.actions().runConsole(action.command(), placeholders);
        else if (player != null) plugin.actions().runPlayer(player, action.command(), placeholders);
    }

    private void particle(Triggers.ParticleAction action, Player player) {
        var targets = targets(player, action.all());
        if (targets.isEmpty()) return;
        var origin = anchor(action.anchor());
        if (action.anchor() == Triggers.Anchor.AROUND) {
            for (int i = 0; i < action.count(); i++) emit(targets, action, ring(origin, action.spread()), 1, 0);
            return;
        }
        emit(targets, action, origin, action.count(), action.spread());
    }

    private void emit(List<Player> targets, Triggers.ParticleAction action, double[] at, int count, double offset) {
        for (var target : targets) {
            target.spawnParticle(action.type(), at[0], at[1], at[2], count, offset, offset, offset, action.speed(), action.data());
        }
    }

    private double[] ring(double[] centre, double radius) {
        var random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * FULL_TURN;
        double lift = (random.nextDouble() - RING_VARIANCE) * npc.eyeHeight() * RING_VARIANCE;
        return new double[]{centre[0] + Math.cos(angle) * radius, centre[1] + lift, centre[2] + Math.sin(angle) * radius};
    }

    private double[] anchor(Triggers.Anchor anchor) {
        double x = npc.x();
        double y = npc.y();
        double z = npc.z();
        double eye = npc.eyeHeight();
        double yaw = Math.toRadians(npc.facing());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double scale = npc.scale();
        return switch (anchor) {
            case HEAD -> new double[]{x, y + eye, z};
            case FEET -> new double[]{x, y + FEET_LIFT * scale, z};
            case BODY, AROUND -> new double[]{x, y + eye * BODY_FACTOR, z};
            case ABOVE -> new double[]{x, y + eye + npc.nameplateHeight() + ABOVE_LIFT * scale, z};
            case HAND -> side(x, y, z, eye, forwardX, forwardZ, rightX, rightZ, 1, scale);
            case OFFHAND -> side(x, y, z, eye, forwardX, forwardZ, rightX, rightZ, -1, scale);
        };
    }

    private double[] side(double x, double y, double z, double eye, double forwardX, double forwardZ, double rightX, double rightZ, int sign, double scale) {
        double side = HAND_SIDE * scale * sign;
        double forward = HAND_FORWARD * scale;
        return new double[]{x + rightX * side + forwardX * forward, y + eye * HAND_FACTOR, z + rightZ * side + forwardZ * forward};
    }
}
