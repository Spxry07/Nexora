package net.spxry.nexora.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

final class GuiListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NexoraGui holder) holder.handleClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NexoraGui holder) holder.handleDrag(event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NexoraGui holder) holder.handleClose(event);
    }
}
