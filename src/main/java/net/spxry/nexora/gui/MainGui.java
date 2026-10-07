package net.spxry.nexora.gui;

import net.spxry.nexora.object.NexoraObject;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import java.util.Map;

final class MainGui extends NexoraGui {
    private static final String FILE = "main";

    MainGui(GuiManager gui, Player player) {
        super(gui, player);
    }

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        return Map.of();
    }

    @Override
    protected void populate() {
        var objects = plugin.objects();
        Map<String, String> ph = Map.of(
            "holograms", String.valueOf(objects.list(NexoraObject.HOLOGRAM).size()),
            "npcs", String.valueOf(objects.list(NexoraObject.NPC).size()),
            "version", plugin.getPluginMeta().getVersion());
        put("info", ph, null);
        put("holograms", ph, click -> gui.openList(player, NexoraObject.HOLOGRAM));
        put("npcs", ph, click -> gui.openList(player, NexoraObject.NPC));
        put("create-hologram", ph, click -> gui.startCreate(player, NexoraObject.HOLOGRAM));
        put("create-npc", ph, click -> gui.startCreate(player, NexoraObject.NPC));
        put("demo", ph, this::demo);
        if (gui.webAvailable(player)) put("web", ph, click -> web());
        put("reload", ph, click -> reload());
        put("dialogs", ph, click -> plugin.menus().openMain(player));
        put("close", ph, click -> player.closeInventory());
    }

    private void demo(ClickType click) {
        if (click.isRightClick()) plugin.menus().clearDemo(player);
        else plugin.menus().spawnDemo(player);
        render();
    }

    private void web() {
        player.closeInventory();
        plugin.web().sendLink(player);
    }

    private void reload() {
        plugin.reloadAll();
        gui.reload();
        gui.notice(player, "reloaded", Map.of());
        render();
    }
}
