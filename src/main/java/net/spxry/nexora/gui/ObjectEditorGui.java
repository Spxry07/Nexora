package net.spxry.nexora.gui;

import net.spxry.nexora.object.NexoraObject;
import org.bukkit.entity.Player;
import java.util.Map;

abstract class ObjectEditorGui extends NexoraGui {
    private static final String FILE = "editor";
    private static final String SECTION_SLOTS = "section-slots";
    private static final String SECTION_LORE = "section.lore";
    private static final String SECTION_ICONS = "section-icons.";
    private static final String DEFAULT_ICON = "default";
    private static final String MAX_WAYPOINTS = "limits.max-waypoints";

    protected final NexoraObject object;

    protected ObjectEditorGui(GuiManager gui, Player player, NexoraObject object) {
        super(gui, player);
        this.object = object;
    }

    protected abstract void extras(Map<String, String> ph);

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        return Map.of("id", object.id(), "kind", gui.kindName(object.kind(), "singular"));
    }

    @Override
    protected void populate() {
        var ph = gui.placeholders(object);
        set(slot("info"), gui.decorate(item("info", ph), object), null);
        sections();
        extras(ph);
        put("triggers", ph, click -> gui.openTriggers(player, object));
        put("back", ph, click -> gui.openList(player, object.kind()));
        put("move-here", ph, click -> moveHere());
        put("teleport", ph, click -> gui.teleport(player, object));
        put("path-add", ph, click -> addPoint());
        put("path-clear", ph, click -> clearPath());
        put("delete", ph, click -> gui.confirmDelete(player, object, p -> gui.openEditor(p, object)));
    }

    private void sections() {
        var sections = plugin.schema().sections(object.kind());
        var slots = cfg.slots(FILE, SECTION_SLOTS);
        var fallback = cfg.string(FILE, SECTION_ICONS + DEFAULT_ICON, "");
        for (int i = 0; i < Math.min(sections.size(), slots.size()); i++) {
            var section = sections.get(i);
            var icon = cfg.string(FILE, SECTION_ICONS + section.id(), fallback);
            var stack = cfg.make(icon, section.title(), cfg.list(FILE, SECTION_LORE), false, Map.of("count", String.valueOf(section.props().size())));
            set(slots.get(i), stack, click -> gui.openSection(player, gui.objectTarget(object), section));
        }
    }

    private void moveHere() {
        var location = player.getLocation();
        gui.mutate(player, object, o -> o.moveTo(location), () -> {
            gui.notice(player, "moved", Map.of("id", object.id()));
            refresh();
        });
    }

    private void addPoint() {
        if (object.waypointCount() >= plugin.getConfig().getInt(MAX_WAYPOINTS)) {
            gui.notice(player, "max-waypoints", Map.of());
            return;
        }
        var location = player.getLocation();
        gui.mutate(player, object, o -> o.addWaypoint(location), () -> {
            gui.notice(player, "point-added", Map.of("count", String.valueOf(object.waypointCount())));
            refresh();
        });
    }

    private void clearPath() {
        gui.mutate(player, object, NexoraObject::clearWaypoints, () -> {
            gui.notice(player, "point-cleared", Map.of());
            refresh();
        });
    }
}
