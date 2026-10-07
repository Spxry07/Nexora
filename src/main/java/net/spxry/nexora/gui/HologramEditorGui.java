package net.spxry.nexora.gui;

import net.spxry.nexora.object.Hologram;
import org.bukkit.entity.Player;
import java.util.Map;

final class HologramEditorGui extends ObjectEditorGui {
    private static final String LAYOUT_PROP = "layout";
    private static final String SHAPE_PICKER = "shape";

    private final Hologram holo;

    HologramEditorGui(GuiManager gui, Player player, Hologram holo) {
        super(gui, player, holo);
        this.holo = holo;
    }

    @Override
    protected void extras(Map<String, String> ph) {
        put("lines", ph, click -> gui.openLines(player, holo));
        put("shape", ph, click -> gui.openPropPicker(player, gui.objectTarget(holo), LAYOUT_PROP, SHAPE_PICKER, p -> gui.openEditor(p, holo)));
    }
}
