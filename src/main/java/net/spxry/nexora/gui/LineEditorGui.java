package net.spxry.nexora.gui;

import net.spxry.nexora.object.Hologram;
import net.spxry.nexora.object.NexoraObject;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;

final class LineEditorGui extends NexoraGui {
    private static final String FILE = "line-editor";
    private static final String SECTION_SLOTS = "section-slots";
    private static final String SECTION_LORE = "section.lore";
    private static final String SECTION_ICONS = "section-icons.";
    private static final String DEFAULT_ICON = "default";
    private static final String EFFECT_PROP = "effect";
    private static final String EFFECT_PICKER = "effect";

    private final Hologram holo;
    private final LineView view;

    LineEditorGui(GuiManager gui, Player player, Hologram holo, LineView view) {
        super(gui, player);
        this.holo = holo;
        this.view = view;
    }

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        return Map.of("id", holo.id(), "number", String.valueOf(view.index() + 1));
    }

    @Override
    protected void populate() {
        Map<String, String> ph = new HashMap<>();
        ph.put("id", holo.id());
        ph.put("number", String.valueOf(view.index() + 1));
        ph.put("size", String.valueOf(view.size()));
        ph.put("type", view.type());
        ph.put("preview", view.preview());
        ph.put("icon", view.icon());
        put("info", ph, null);
        sections();
        put("effect", ph, click -> gui.openPropPicker(player, gui.lineTarget(holo, view.index()), EFFECT_PROP, EFFECT_PICKER, p -> gui.openLineEditor(p, holo, view.index())));
        if (view.index() > 0) put("line-up", ph, click -> move(-1));
        if (view.index() < view.size() - 1) put("line-down", ph, click -> move(1));
        put("back", ph, click -> gui.openLines(player, holo));
        put("delete", ph, click -> delete());
    }

    private void sections() {
        var sections = plugin.schema().sections(NexoraObject.LINE);
        var slots = cfg.slots(FILE, SECTION_SLOTS);
        var fallback = cfg.string(FILE, SECTION_ICONS + DEFAULT_ICON, "");
        for (int i = 0; i < Math.min(sections.size(), slots.size()); i++) {
            var section = sections.get(i);
            var icon = cfg.string(FILE, SECTION_ICONS + section.id(), fallback);
            var stack = cfg.make(icon, section.title(), cfg.list(FILE, SECTION_LORE), false, Map.of("count", String.valueOf(section.props().size())));
            set(slots.get(i), stack, click -> gui.openSection(player, gui.lineTarget(holo, view.index()), section));
        }
    }

    private void move(int delta) {
        var index = view.index();
        var target = index + delta;
        gui.mutate(player, holo, h -> {
            var lines = h.lines();
            if (index >= lines.size() || target < 0 || target >= lines.size()) return;
            var moved = lines.get(index);
            lines.set(index, lines.get(target));
            lines.set(target, moved);
        }, () -> gui.openLineEditor(player, holo, target));
    }

    private void delete() {
        var index = view.index();
        gui.mutate(player, holo, h -> {
            if (index < h.lines().size()) h.lines().remove(index);
        }, () -> {
            gui.notice(player, "line-deleted", Map.of());
            gui.openLines(player, holo);
        });
    }
}
