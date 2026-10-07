package net.spxry.nexora.gui;

import net.spxry.nexora.object.NexoraObject;
import net.spxry.nexora.util.ColorUtil;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TriggersGui extends NexoraGui {
    private static final String FILE = "triggers";
    private static final String SLOTS_PATH = "slots";
    private static final String RULES_PATH = "rules";
    private static final String TRIGGERS_PROP = "triggers";
    private static final String COMMENT_PREFIX = "#";
    private static final String RULE_SEPARATOR = "\n";
    private static final String LORE_PREFIX = "\n&7";
    private static final int DEFAULT_PREVIEW_RULES = 5;
    private static final int DEFAULT_PREVIEW_LENGTH = 40;

    private final NexoraObject object;
    private String triggers;

    TriggersGui(GuiManager gui, Player player, NexoraObject object, String triggers) {
        super(gui, player);
        this.object = object;
        this.triggers = triggers == null ? "" : triggers;
    }

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        return Map.of("id", object.id());
    }

    @Override
    protected void populate() {
        var active = activeRules();
        Map<String, String> ph = new HashMap<>();
        ph.put("id", object.id());
        ph.put("count", String.valueOf(active.size()));
        ph.put("rules", preview(active));
        put("current", ph, null);
        starters();
        put("raw", ph, click -> editRaw());
        put("clear", ph, click -> clear());
        if (gui.webAvailable(player)) put("web", ph, click -> web());
        put("back", ph, click -> gui.openEditor(player, object));
    }

    private void starters() {
        var section = cfg.section(FILE, RULES_PATH);
        if (section == null) return;
        var slots = cfg.slots(FILE, SLOTS_PATH);
        var index = 0;
        for (var key : section.getKeys(false)) {
            var rule = section.getConfigurationSection(key);
            if (rule == null || index >= slots.size() || !rule.getStringList("kinds").contains(object.kind())) continue;
            var text = rule.getString("rule", "");
            var shown = ColorUtil.plain(text).replace(RULE_SEPARATOR, LORE_PREFIX);
            var ph = Map.of("description", rule.getString("description", ""), "rule", shown);
            var stack = cfg.make(rule.getString("material", ""), rule.getString("name", ""), cfg.list(FILE, "starter.lore"), false, ph);
            set(slots.get(index++), stack, click -> add(text));
        }
    }

    private List<String> activeRules() {
        List<String> out = new ArrayList<>();
        for (var line : triggers.split(RULE_SEPARATOR)) {
            var trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith(COMMENT_PREFIX)) out.add(trimmed);
        }
        return out;
    }

    private String preview(List<String> rules) {
        if (rules.isEmpty()) return gui.value("no-rules");
        var limit = cfg.integer(FILE, "preview-rules", DEFAULT_PREVIEW_RULES);
        var length = cfg.integer(FILE, "preview-length", DEFAULT_PREVIEW_LENGTH);
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, rules.size()); i++) shown.add(gui.truncate(ColorUtil.plain(rules.get(i)), length));
        return String.join(LORE_PREFIX, shown);
    }

    private void add(String rule) {
        gui.mutate(player, object, o -> {
            var existing = o.values().getOrDefault(TRIGGERS_PROP, "");
            o.apply(Map.of(TRIGGERS_PROP, existing.isBlank() ? rule : existing.stripTrailing() + RULE_SEPARATOR + rule));
        }, () -> {
            gui.notice(player, "rule-added", Map.of());
            reload();
        });
    }

    private void clear() {
        gui.mutate(player, object, o -> o.apply(Map.of(TRIGGERS_PROP, "")), () -> {
            gui.notice(player, "rules-cleared", Map.of());
            reload();
        });
    }

    private void reload() {
        gui.load(player, object, o -> o.values().getOrDefault(TRIGGERS_PROP, ""), latest -> {
            triggers = latest;
            refresh();
        });
    }

    private void web() {
        player.closeInventory();
        plugin.web().sendLink(player);
    }

    private void editRaw() {
        var prop = plugin.schema().props(object.kind()).get(TRIGGERS_PROP);
        var ph = Map.of("id", object.id());
        var label = prop == null ? text("prompt.label", ph) : ColorUtil.colorize(prop.label());
        var length = prop == null || prop.max() <= 0 ? cfg.integer(GuiConfig.COMMON, "prompt.default-max-length", triggers.length()) : (int) prop.max();
        var spec = new TextPrompt.Spec(text("prompt.title", ph), text("prompt.body", ph), label, triggers, length, true);
        gui.prompt(player, spec, this::saveRaw, () -> gui.openTriggers(player, object));
    }

    private void saveRaw(String value) {
        if (value == null) {
            gui.openTriggers(player, object);
            return;
        }
        gui.mutate(player, object, o -> o.apply(Map.of(TRIGGERS_PROP, value)), () -> {
            gui.notice(player, "saved", Map.of());
            gui.openTriggers(player, object);
        });
    }
}
