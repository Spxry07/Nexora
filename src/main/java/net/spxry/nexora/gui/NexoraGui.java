package net.spxry.nexora.gui;

import net.kyori.adventure.text.Component;
import net.spxry.nexora.Nexora;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

abstract class NexoraGui implements InventoryHolder {
    private static final int SLOTS_PER_ROW = 9;
    private static final int MAX_ROWS = 6;
    private static final int DEFAULT_SIZE = 54;
    private static final String SIZE_PATH = "size";
    private static final String TITLE_PATH = "title";
    private static final String SOUND_OPEN = "open";
    private static final String SOUND_CLICK = "click";

    protected final GuiManager gui;
    protected final Nexora plugin;
    protected final GuiConfig cfg;
    protected final Player player;
    private final Map<Integer, Consumer<ClickType>> handlers = new ConcurrentHashMap<>();
    private volatile Inventory inventory;

    protected NexoraGui(GuiManager gui, Player player) {
        this.gui = gui;
        this.plugin = gui.plugin();
        this.cfg = gui.config();
        this.player = player;
    }

    protected abstract String file();

    protected abstract Map<String, String> titlePlaceholders();

    protected abstract void populate();

    protected String titlePath() {
        return TITLE_PATH;
    }

    protected Component title() {
        return cfg.text(file(), titlePath(), titlePlaceholders());
    }

    protected int size() {
        var rows = (cfg.integer(file(), SIZE_PATH, DEFAULT_SIZE) + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
        return Math.clamp(rows, 1, MAX_ROWS) * SLOTS_PER_ROW;
    }

    final Player player() {
        return player;
    }

    final void open() {
        inventory = Bukkit.createInventory(this, size(), title());
        render();
        player.openInventory(inventory);
        gui.sound(player, SOUND_OPEN);
    }

    final void render() {
        var current = inventory;
        if (current == null) return;
        handlers.clear();
        current.clear();
        populate();
        var filler = cfg.filler();
        for (int slot = 0; slot < current.getSize(); slot++) {
            var item = current.getItem(slot);
            if (item == null || item.isEmpty()) current.setItem(slot, filler.clone());
        }
    }

    final boolean isOpen() {
        var current = inventory;
        return current != null && player.getOpenInventory().getTopInventory().getHolder() == this;
    }

    final void refresh() {
        if (isOpen()) render();
    }

    protected final int slot(String key) {
        return cfg.slot(file(), key);
    }

    protected final ItemStack item(String key, Map<String, String> ph) {
        return cfg.item(file(), key, ph);
    }

    protected final void set(int slot, ItemStack item, Consumer<ClickType> handler) {
        var current = inventory;
        if (current == null || item == null || slot < 0 || slot >= current.getSize()) return;
        current.setItem(slot, item);
        if (handler != null) handlers.put(slot, handler);
    }

    protected final void put(String key, Map<String, String> ph, Consumer<ClickType> handler) {
        set(slot(key), item(key, ph), handler);
    }

    protected final Component text(String path, Map<String, String> ph) {
        return cfg.text(file(), path, ph);
    }

    void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        var handler = handlers.get(event.getRawSlot());
        if (handler == null) return;
        gui.sound(player, SOUND_CLICK);
        handler.accept(event.getClick());
    }

    void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    void handleClose(InventoryCloseEvent event) {}

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
