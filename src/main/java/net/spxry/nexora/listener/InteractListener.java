package net.spxry.nexora.listener;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.object.Npc;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

public final class InteractListener implements Listener {
    private final Nexora plugin;

    public InteractListener(Nexora plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerUseUnknownEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        boolean attack = event.isAttack();
        if (!attack && event.getClickedRelativePosition() != null) return;
        plugin.objects().byEntityId(event.getEntityId())
            .filter(Npc.class::isInstance)
            .map(Npc.class::cast)
            .ifPresent(npc -> npc.click(event.getPlayer(), attack));
    }
}
