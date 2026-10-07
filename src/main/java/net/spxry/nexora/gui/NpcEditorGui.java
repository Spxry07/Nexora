package net.spxry.nexora.gui;

import net.spxry.nexora.object.Npc;
import org.bukkit.entity.Player;
import java.util.Map;

final class NpcEditorGui extends ObjectEditorGui {
    private static final String ANIMATION_PROP = "animation";
    private static final String ANIMATION_PICKER = "animation";

    private final Npc npc;

    NpcEditorGui(GuiManager gui, Player player, Npc npc) {
        super(gui, player, npc);
        this.npc = npc;
    }

    @Override
    protected void extras(Map<String, String> ph) {
        put("equipment", ph, click -> gui.openEquipment(player, npc));
        put("animation", ph, click -> gui.openPropPicker(player, gui.objectTarget(npc), ANIMATION_PROP, ANIMATION_PICKER, p -> gui.openEditor(p, npc)));
        put("nameplate", ph, click -> gui.load(player, npc, plugin.objects()::ensureNameplate, holo -> gui.openLines(player, holo)));
    }
}
