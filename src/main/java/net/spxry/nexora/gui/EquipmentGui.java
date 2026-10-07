package net.spxry.nexora.gui;

import net.spxry.nexora.object.Npc;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class EquipmentGui extends NexoraGui {
    private static final String FILE = "equipment";
    private static final String SLOT_POSITIONS = "slots.";
    private static final String SLOT_NAMES = "slot-names.";
    private static final String PLACEHOLDER = "placeholder.";
    private static final int NO_SLOT = -1;

    private final Npc npc;
    private final Map<EquipmentSlot, ItemStack> worn;
    private final Map<Integer, EquipmentSlot> positions = new ConcurrentHashMap<>();

    EquipmentGui(GuiManager gui, Player player, Npc npc, Map<EquipmentSlot, ItemStack> worn) {
        super(gui, player);
        this.npc = npc;
        this.worn = new ConcurrentHashMap<>(worn);
    }

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        return Map.of("id", npc.id());
    }

    @Override
    protected void populate() {
        positions.clear();
        for (var slot : plugin.menus().slots()) {
            var position = cfg.integer(FILE, SLOT_POSITIONS + slot.name(), NO_SLOT);
            if (position < 0) continue;
            positions.put(position, slot);
            var stored = worn.get(slot);
            set(position, stored == null ? placeholder(slot) : stored.clone(), null);
        }
        Map<String, String> ph = Map.of("id", npc.id());
        put("info", ph, null);
        put("back", ph, click -> gui.openEditor(player, npc));
    }

    private ItemStack placeholder(EquipmentSlot slot) {
        var ph = Map.of("slot", cfg.string(FILE, SLOT_NAMES + slot.name(), slot.name()));
        return cfg.make(cfg.string(FILE, PLACEHOLDER + "materials." + slot.name(), ""), cfg.string(FILE, PLACEHOLDER + "name", ""),
            cfg.list(FILE, PLACEHOLDER + "lore"), false, ph);
    }

    private String slotName(EquipmentSlot slot) {
        return cfg.string(FILE, SLOT_NAMES + slot.name(), slot.name());
    }

    @Override
    void handleClick(InventoryClickEvent event) {
        var top = event.getView().getTopInventory();
        var clicked = event.getClickedInventory();
        if (clicked == null) {
            event.setCancelled(true);
            return;
        }
        var click = event.getClick();
        if (clicked == top) {
            var slot = positions.get(event.getRawSlot());
            if (slot == null) {
                super.handleClick(event);
                return;
            }
            event.setCancelled(true);
            if (click == ClickType.LEFT || click == ClickType.RIGHT) swap(slot);
            else if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) take(slot);
            return;
        }
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            event.setCancelled(true);
            equipFromInventory(event);
            return;
        }
        event.setCancelled(click != ClickType.LEFT && click != ClickType.RIGHT);
    }

    private void swap(EquipmentSlot slot) {
        var cursor = player.getItemOnCursor();
        var stored = worn.get(slot);
        if (cursor.isEmpty()) {
            if (stored == null) return;
            player.setItemOnCursor(stored);
            store(slot, null);
            return;
        }
        var placed = cursor.asOne();
        var remaining = cursor.clone();
        remaining.setAmount(remaining.getAmount() - 1);
        if (stored == null) {
            player.setItemOnCursor(remaining.getAmount() <= 0 ? null : remaining);
        } else if (remaining.getAmount() <= 0) {
            player.setItemOnCursor(stored);
        } else {
            player.setItemOnCursor(remaining);
            give(stored);
        }
        store(slot, placed);
    }

    private void take(EquipmentSlot slot) {
        var stored = worn.get(slot);
        if (stored == null) return;
        give(stored);
        store(slot, null);
    }

    private void equipFromInventory(InventoryClickEvent event) {
        var item = event.getCurrentItem();
        if (item == null || item.isEmpty()) return;
        var target = freeSlot(item);
        if (target == null) {
            gui.notice(player, "no-slot", Map.of());
            return;
        }
        var remaining = item.clone();
        remaining.setAmount(remaining.getAmount() - 1);
        player.getInventory().setItem(event.getSlot(), remaining.getAmount() <= 0 ? null : remaining);
        store(target, item.asOne());
    }

    private EquipmentSlot freeSlot(ItemStack item) {
        List<EquipmentSlot> configured = plugin.menus().slots();
        var preferred = item.getType().getEquipmentSlot();
        if (configured.contains(preferred) && !worn.containsKey(preferred)) return preferred;
        for (var slot : configured) {
            if (!worn.containsKey(slot) && (slot == EquipmentSlot.HAND || slot == EquipmentSlot.OFF_HAND)) return slot;
        }
        return null;
    }

    private void give(ItemStack item) {
        for (var leftover : player.getInventory().addItem(item.clone()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void store(EquipmentSlot slot, ItemStack item) {
        if (item == null) worn.remove(slot);
        else worn.put(slot, item);
        render();
        gui.notice(player, item == null ? "unequipped" : "equipped", Map.of("slot", slotName(slot)));
        plugin.menus().setEquipment(npc, slot, item).exceptionally(ex -> gui.fail(player, npc.id(), ex));
    }
}
