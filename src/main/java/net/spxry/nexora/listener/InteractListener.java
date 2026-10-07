package net.spxry.nexora.listener;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.object.Hologram;
import net.spxry.nexora.object.NexoraObject;
import net.spxry.nexora.object.Npc;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

public final class InteractListener implements Listener {
    private static final String SNEAK_EDIT_KEY = "admin.sneak-click-edit";
    private static final String ADMIN_PERMISSION_KEY = "permissions.admin";

    private final Nexora plugin;

    public InteractListener(Nexora plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerUseUnknownEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        boolean attack = event.isAttack();
        if (!attack && event.getClickedRelativePosition() != null) return;
        var player = event.getPlayer();
        plugin.objects().byEntityId(event.getEntityId()).ifPresent(object -> route(player, object, attack));
    }

    private void route(Player player, NexoraObject object, boolean attack) {
        if (!(object instanceof Npc) && !(object instanceof Hologram)) return;
        if (editRequested(player)) {
            plugin.guis().openEditor(player, object);
            return;
        }
        if (object instanceof Npc npc) npc.click(player, attack);
        else if (object instanceof Hologram hologram) hologram.click(player, attack);
    }

    private boolean editRequested(Player player) {
        if (!plugin.getConfig().getBoolean(SNEAK_EDIT_KEY) || !player.isSneaking()) return false;
        var permission = plugin.getConfig().getString(ADMIN_PERMISSION_KEY);
        return permission != null && player.hasPermission(permission);
    }
}
