package net.spxry.nexora.gui;

import net.spxry.nexora.object.Hologram;
import net.spxry.nexora.util.ItemCodec;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LinesGui extends NexoraGui {
    private static final String FILE = "lines";
    private static final String SLOTS_PATH = "slots";
    private static final String MAX_LINES = "limits.max-lines";
    private static final String PROP_TYPE = "type";
    private static final String PROP_MATERIAL = "material";
    private static final String TYPE_ITEM = "ITEM";
    private static final String TYPE_BLOCK = "BLOCK";

    private final Hologram holo;
    private List<LineView> views;
    private int page;

    LinesGui(GuiManager gui, Player player, Hologram holo, List<LineView> views) {
        super(gui, player);
        this.holo = holo;
        this.views = views;
    }

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        return Map.of("id", holo.id());
    }

    @Override
    protected void populate() {
        var slots = cfg.slots(FILE, SLOTS_PATH);
        var perPage = Math.max(1, slots.size());
        var pages = Math.max(1, (views.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < slots.size(); i++) {
            var index = page * perPage + i;
            if (index >= views.size()) break;
            var view = views.get(index);
            set(slots.get(i), lineItem(view), click -> lineClick(view, click));
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("id", holo.id());
        ph.put("count", String.valueOf(views.size()));
        ph.put("max", String.valueOf(plugin.getConfig().getInt(MAX_LINES)));
        ph.put("page", String.valueOf(page + 1));
        ph.put("pages", String.valueOf(pages));
        put("info", ph, null);
        put("back", ph, click -> gui.openEditor(player, holo));
        put("add-text", ph, click -> addText());
        put("add-item", ph, click -> addHeld(false));
        put("add-block", ph, click -> addHeld(true));
        if (page > 0) put("prev", ph, click -> turn(-1));
        if (page < pages - 1) put("next", ph, click -> turn(1));
    }

    private ItemStack lineItem(LineView view) {
        Map<String, String> ph = Map.of("number", String.valueOf(view.index() + 1), "type", view.type(), "preview", view.preview());
        return cfg.make(view.icon(), cfg.string(FILE, "entry.name", ""), cfg.list(FILE, "entry.lore"), false, ph);
    }

    private void turn(int delta) {
        page += delta;
        render();
    }

    private void lineClick(LineView view, ClickType click) {
        if (click == ClickType.SHIFT_LEFT) move(view.index(), -1);
        else if (click == ClickType.SHIFT_RIGHT) move(view.index(), 1);
        else if (click == ClickType.LEFT) gui.openLineEditor(player, holo, view.index());
    }

    private void move(int index, int delta) {
        var target = index + delta;
        if (target < 0 || target >= views.size()) return;
        gui.mutate(player, holo, h -> {
            var lines = h.lines();
            if (index >= lines.size() || target >= lines.size()) return;
            var moved = lines.get(index);
            lines.set(index, lines.get(target));
            lines.set(target, moved);
        }, this::reload);
    }

    private void reload() {
        gui.load(player, holo, gui::lineViews, loaded -> {
            views = loaded;
            refresh();
        });
    }

    private void addText() {
        add(Map.of(), null);
    }

    private void addHeld(boolean block) {
        var item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            gui.notice(player, "no-item", Map.of());
            return;
        }
        if (!block) {
            add(Map.of(PROP_TYPE, TYPE_ITEM), ItemCodec.encode(item.asOne()));
            return;
        }
        if (!item.getType().isBlock()) {
            gui.notice(player, "no-block", Map.of());
            return;
        }
        add(Map.of(PROP_TYPE, TYPE_BLOCK, PROP_MATERIAL, item.getType().name()), null);
    }

    private void add(Map<String, String> props, String itemData) {
        var max = String.valueOf(plugin.getConfig().getInt(MAX_LINES));
        plugin.menus().addLine(holo, props, itemData)
            .thenAccept(added -> gui.hop(player, () -> {
                gui.notice(player, added ? "line-added" : "line-max", Map.of("max", max));
                reload();
            }))
            .exceptionally(ex -> gui.fail(player, holo.id(), ex));
    }
}
