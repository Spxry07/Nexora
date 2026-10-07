package net.spxry.nexora.gui;

import net.spxry.nexora.object.NexoraObject;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ListGui extends NexoraGui {
    private static final String FILE = "list";
    private static final String SLOTS_PATH = "slots";
    private static final String PROMPT_MAX_PATH = "prompt.max-length";
    private static final int DEFAULT_PROMPT_LENGTH = 32;

    private final String kind;
    private final String query;
    private int page;

    ListGui(GuiManager gui, Player player, String kind, int page, String query) {
        super(gui, player);
        this.kind = kind;
        this.page = page;
        this.query = query == null ? "" : query;
    }

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        return kindPlaceholders();
    }

    @Override
    protected void populate() {
        var entries = entries();
        var slots = cfg.slots(FILE, SLOTS_PATH);
        var perPage = Math.max(1, slots.size());
        var pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < slots.size(); i++) {
            var index = page * perPage + i;
            if (index >= entries.size()) break;
            var obj = entries.get(index);
            set(slots.get(i), entry(obj), click -> entryClick(obj, click));
        }
        Map<String, String> ph = new HashMap<>(kindPlaceholders());
        ph.put("page", String.valueOf(page + 1));
        ph.put("pages", String.valueOf(pages));
        ph.put("count", String.valueOf(entries.size()));
        ph.put("filter", query.isEmpty() ? cfg.string(FILE, "values.no-filter", "") : query);
        put("info", ph, null);
        put("back", ph, click -> gui.openMain(player));
        put("search", ph, click -> search());
        put("create", ph, click -> gui.startCreate(player, kind));
        if (!query.isEmpty()) put("clear-search", ph, click -> gui.openList(player, kind, 0, ""));
        if (page > 0) put("prev", ph, click -> turn(-1));
        if (page < pages - 1) put("next", ph, click -> turn(1));
    }

    private Map<String, String> kindPlaceholders() {
        return Map.of("kind", gui.kindName(kind, "singular"), "kinds", gui.kindName(kind, "plural"), "query", query);
    }

    private List<NexoraObject> entries() {
        var needle = query.toLowerCase(Locale.ROOT);
        return plugin.objects().list(kind).stream()
            .filter(obj -> needle.isEmpty() || obj.id().toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    }

    private ItemStack entry(NexoraObject obj) {
        var base = "entry." + kind;
        var stack = cfg.make(cfg.string(FILE, base + ".material", ""), cfg.string(FILE, "entry.name", ""), cfg.list(FILE, base + ".lore"), false, gui.placeholders(obj));
        return gui.decorate(stack, obj);
    }

    private void turn(int delta) {
        page += delta;
        render();
    }

    private void entryClick(NexoraObject obj, ClickType click) {
        if (click == ClickType.SHIFT_RIGHT) gui.confirmDelete(player, obj, p -> gui.openList(p, kind, page, query));
        else if (click == ClickType.RIGHT) gui.teleport(player, obj);
        else if (click.isLeftClick()) gui.openEditor(player, obj);
    }

    private void search() {
        var ph = kindPlaceholders();
        var spec = new TextPrompt.Spec(text("prompt.title", ph), text("prompt.body", ph), text("prompt.label", ph), query,
            cfg.integer(FILE, PROMPT_MAX_PATH, DEFAULT_PROMPT_LENGTH), false);
        gui.prompt(player, spec,
            value -> gui.openList(player, kind, 0, value == null ? query : value.trim()),
            () -> gui.openList(player, kind, page, query));
    }
}
