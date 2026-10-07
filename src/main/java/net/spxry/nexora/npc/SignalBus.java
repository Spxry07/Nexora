package net.spxry.nexora.npc;

import net.spxry.nexora.Nexora;
import org.bukkit.entity.Player;

public final class SignalBus {
    public record Chain(String name, Chain parent, int depth, boolean detached) {
        boolean contains(String other) {
            for (var node = this; node != null; node = node.parent) {
                if (!node.detached && node.name.equals(other)) return true;
            }
            return false;
        }

        Chain detach() {
            return new Chain(name, null, 0, true);
        }
    }

    private static final String MAX_DEPTH_KEY = "triggers.max-signal-depth";
    private static final int DEFAULT_MAX_DEPTH = 4;

    private final Nexora plugin;

    public SignalBus(Nexora plugin) {
        this.plugin = plugin;
    }

    public void emit(String name, Player source) {
        emit(name, source, null);
    }

    void emit(String raw, Player source, Chain parent) {
        var name = Triggers.signalName(raw);
        if (name == null) return;
        int depth = parent == null ? 1 : parent.depth() + 1;
        if (depth > plugin.getConfig().getInt(MAX_DEPTH_KEY, DEFAULT_MAX_DEPTH)) return;
        if (parent != null && parent.contains(name)) return;
        var chain = new Chain(name, parent, depth, false);
        for (var object : plugin.objects().all()) {
            if (object.isRemoved() || !(object instanceof HasRuntime has)) continue;
            var runtime = has.runtime();
            if (runtime == null || !runtime.listens(name)) continue;
            plugin.scheduler().runAtLocation(object.anchor(), () -> runtime.signal(name, source, chain));
        }
    }
}
