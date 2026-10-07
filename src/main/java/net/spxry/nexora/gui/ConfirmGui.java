package net.spxry.nexora.gui;

import org.bukkit.entity.Player;
import java.util.Map;

final class ConfirmGui extends NexoraGui {
    private static final String FILE = "confirm";

    private final Map<String, String> placeholders;
    private final Runnable confirm;
    private final Runnable cancel;

    ConfirmGui(GuiManager gui, Player player, Map<String, String> placeholders, Runnable confirm, Runnable cancel) {
        super(gui, player);
        this.placeholders = placeholders;
        this.confirm = confirm;
        this.cancel = cancel;
    }

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        return placeholders;
    }

    @Override
    protected void populate() {
        put("cancel", placeholders, click -> cancel.run());
        put("info", placeholders, null);
        put("confirm", placeholders, click -> confirm.run());
    }
}
