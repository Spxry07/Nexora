package net.spxry.nexora.gui;

import net.spxry.nexora.config.MessageManager;
import net.spxry.nexora.edit.Schema;
import net.spxry.nexora.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class PickerGui extends NexoraGui {
    private static final String FILE = "picker";
    private static final String SLOTS_PATH = "slots";
    private static final String PICKERS = "pickers.";
    private static final String MODE_EGG = "egg";

    private final String key;
    private final Map<String, String> placeholders;
    private final List<Schema.Option> options;
    private final String current;
    private final Consumer<String> pick;
    private final Consumer<Player> back;
    private int page;

    PickerGui(GuiManager gui, Player player, String key, Map<String, String> placeholders, List<Schema.Option> options, String current,
              Consumer<String> pick, Consumer<Player> back) {
        super(gui, player);
        this.key = key;
        this.placeholders = placeholders;
        this.options = options;
        this.current = current == null ? "" : current;
        this.pick = pick;
        this.back = back;
    }

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected String titlePath() {
        return PICKERS + key + ".title";
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        return placeholders;
    }

    @Override
    protected void populate() {
        var slots = cfg.slots(FILE, SLOTS_PATH);
        var perPage = Math.max(1, slots.size());
        var pages = Math.max(1, (options.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < slots.size(); i++) {
            var index = page * perPage + i;
            if (index >= options.size()) break;
            var option = options.get(index);
            set(slots.get(i), entry(option), click -> pick.accept(option.id()));
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("title", ColorUtil.plain(MessageManager.apply(cfg.string(FILE, titlePath(), ""), placeholders)));
        ph.put("count", String.valueOf(options.size()));
        ph.put("page", String.valueOf(page + 1));
        ph.put("pages", String.valueOf(pages));
        ph.put("current", current.isEmpty() ? gui.value("empty") : current);
        put("info", ph, null);
        put("back", ph, click -> back.accept(player));
        if (page > 0) put("prev", ph, click -> turn(-1));
        if (page < pages - 1) put("next", ph, click -> turn(1));
    }

    private ItemStack entry(Schema.Option option) {
        var selected = option.id().equalsIgnoreCase(current);
        Map<String, String> ph = Map.of("id", option.id(), "label", SmallCaps.of(option.label()), "plain", option.label());
        var lore = cfg.list(FILE, selected ? "entry.selected-lore" : "entry.lore");
        return cfg.make(icon(option.id()), cfg.string(FILE, "entry.name", ""), lore, selected, ph);
    }

    private String icon(String optionId) {
        var base = PICKERS + key;
        if (MODE_EGG.equals(cfg.string(FILE, base + ".mode", ""))) return gui.eggMaterial(optionId);
        return cfg.string(FILE, base + ".icons." + optionId, cfg.string(FILE, base + ".default-icon", ""));
    }

    private void turn(int delta) {
        page += delta;
        render();
    }
}
