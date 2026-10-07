package net.spxry.nexora.gui;

import net.spxry.nexora.edit.Schema;
import net.spxry.nexora.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class PropGui extends NexoraGui {
    private static final String FILE = "props";
    private static final String SLOTS_PATH = "slots";
    private static final String TYPES = "types.";
    private static final String COLOR_VALUE = "&%s";
    private static final String RANGE_FORMAT = "%s - %s";
    private static final String CHOICE_PICKER = "choice";
    private static final String MULTIPLIER_PATH = "numbers.shift-multiplier";
    private static final String STEP_PATH = "numbers.default-step";
    private static final String COLOR_LENGTH_PATH = "prompt.color-max-length";
    private static final String TEXT_LENGTH_PATH = "prompt.default-max-length";
    private static final int DEFAULT_COLOR_LENGTH = 9;
    private static final int DEFAULT_TEXT_LENGTH = 256;
    private static final double DEFAULT_STEP = 1;
    private static final double DEFAULT_MULTIPLIER = 10;
    private static final Pattern SIMPLE_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private final PropTarget target;
    private final Schema.Section section;
    private final Map<String, String> values;
    private int pending;

    PropGui(GuiManager gui, Player player, PropTarget target, Schema.Section section, Map<String, String> values) {
        super(gui, player);
        this.target = target;
        this.section = section;
        this.values = new HashMap<>(values);
    }

    @Override
    protected String file() {
        return FILE;
    }

    @Override
    protected Map<String, String> titlePlaceholders() {
        Map<String, String> ph = new HashMap<>(target.placeholders());
        ph.put("section", section.title());
        return ph;
    }

    @Override
    protected void populate() {
        var slots = cfg.slots(FILE, SLOTS_PATH);
        var props = section.props();
        for (int i = 0; i < Math.min(props.size(), slots.size()); i++) {
            var prop = props.get(i);
            set(slots.get(i), propItem(prop), click -> click(prop, click));
        }
        put("back", target.placeholders(), click -> target.back().accept(player));
    }

    private ItemStack propItem(Schema.Prop prop) {
        var raw = current(prop);
        var base = TYPES + prop.type().name();
        Map<String, String> ph = new HashMap<>();
        ph.put("value", display(prop, raw));
        ph.put("min", Schema.format(prop.min()));
        ph.put("max", Schema.format(prop.max()));
        ph.put("step", Schema.format(step(prop)));
        ph.put("multiplier", Schema.format(cfg.decimal(GuiConfig.COMMON, MULTIPLIER_PATH, DEFAULT_MULTIPLIER)));
        ph.put("range", prop.max() > prop.min() ? RANGE_FORMAT.formatted(Schema.format(prop.min()), Schema.format(prop.max())) : gui.value("any-range"));
        ph.put("count", String.valueOf(prop.options().size()));
        var material = prop.type() == Schema.Type.BOOL
            ? cfg.string(FILE, base + (Boolean.parseBoolean(raw) ? ".material-on" : ".material-off"), "")
            : cfg.string(FILE, base + ".material", "");
        return cfg.make(material, prop.label(), cfg.list(FILE, base + ".lore"), false, ph);
    }

    private String display(Schema.Prop prop, String raw) {
        return switch (prop.type()) {
            case BOOL -> gui.value(Boolean.parseBoolean(raw) ? "enabled" : "disabled");
            case CHOICE -> raw.isEmpty() ? gui.value("empty") : gui.optionLabel(target.kind(), prop.id(), raw);
            case NUMBER, INTEGER -> raw.isEmpty() ? gui.value("empty") : raw;
            case TEXT, MULTILINE -> gui.preview(raw);
            case COLOR -> raw.isEmpty() ? gui.value("empty") : SIMPLE_COLOR.matcher(raw).matches() ? COLOR_VALUE.formatted(raw) + raw : raw;
        };
    }

    private String current(Schema.Prop prop) {
        return values.getOrDefault(prop.id(), "");
    }

    private double step(Schema.Prop prop) {
        return prop.step() > 0 ? prop.step() : cfg.decimal(GuiConfig.COMMON, STEP_PATH, DEFAULT_STEP);
    }

    private void click(Schema.Prop prop, ClickType click) {
        switch (prop.type()) {
            case BOOL -> set(prop, String.valueOf(!Boolean.parseBoolean(current(prop))));
            case CHOICE -> choice(prop, click);
            case NUMBER, INTEGER -> number(prop, click);
            case TEXT, MULTILINE, COLOR -> prompt(prop);
        }
    }

    private void choice(Schema.Prop prop, ClickType click) {
        var options = prop.options();
        if (options.isEmpty()) {
            prompt(prop);
            return;
        }
        if (click.isShiftClick()) {
            gui.openPropPicker(player, target, prop.id(), CHOICE_PICKER, p -> gui.openSection(p, target, section));
            return;
        }
        var index = -1;
        for (int i = 0; i < options.size(); i++) if (options.get(i).id().equalsIgnoreCase(current(prop))) index = i;
        var delta = click.isRightClick() ? -1 : 1;
        var next = Math.floorMod(index + delta, options.size());
        set(prop, options.get(next).id());
    }

    private void number(Schema.Prop prop, ClickType click) {
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            prompt(prop);
            return;
        }
        if (!click.isLeftClick() && !click.isRightClick()) return;
        var multiplier = click.isShiftClick() ? cfg.decimal(GuiConfig.COMMON, MULTIPLIER_PATH, DEFAULT_MULTIPLIER) : 1;
        var delta = step(prop) * multiplier * (click.isLeftClick() ? 1 : -1);
        set(prop, Schema.format(Schema.clamp(prop, parse(current(prop), prop.min()) + delta)));
    }

    private double parse(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void set(Schema.Prop prop, String raw) {
        var normalized = Schema.normalize(prop, raw);
        if (normalized == null) {
            gui.notice(player, "invalid", Map.of("fields", ColorUtil.plain(prop.label())));
            return;
        }
        values.put(prop.id(), normalized);
        render();
        pending++;
        target.save().apply(Map.of(prop.id(), normalized))
            .whenComplete((invalid, error) -> gui.hop(player, () -> saved(prop, invalid, error)));
    }

    private void saved(Schema.Prop prop, List<String> invalid, Throwable error) {
        pending--;
        if (error != null) {
            gui.notice(player, "not-found", Map.of("id", target.placeholders().getOrDefault("id", "")));
            return;
        }
        if (!invalid.isEmpty()) gui.notice(player, "invalid", Map.of("fields", ColorUtil.plain(prop.label())));
        if (pending == 0) reload();
    }

    private void reload() {
        target.values().get()
            .thenAccept(latest -> gui.hop(player, () -> {
                if (latest == null) {
                    target.back().accept(player);
                    return;
                }
                values.clear();
                values.putAll(latest);
                refresh();
            }))
            .exceptionally(ex -> gui.fail(player, target.placeholders().get("id"), ex));
    }

    private void prompt(Schema.Prop prop) {
        Map<String, String> ph = new HashMap<>(target.placeholders());
        ph.put("prop", ColorUtil.plain(prop.label()));
        var fallback = prop.type() == Schema.Type.COLOR
            ? cfg.integer(GuiConfig.COMMON, COLOR_LENGTH_PATH, DEFAULT_COLOR_LENGTH)
            : cfg.integer(GuiConfig.COMMON, TEXT_LENGTH_PATH, DEFAULT_TEXT_LENGTH);
        var spec = new TextPrompt.Spec(text("prompt.title", ph), text("prompt.body", ph), ColorUtil.colorize(prop.label()), current(prop),
            prop.max() > 0 ? (int) prop.max() : fallback, prop.type() == Schema.Type.MULTILINE);
        gui.prompt(player, spec, value -> submit(prop, value), () -> gui.openSection(player, target, section));
    }

    private void submit(Schema.Prop prop, String value) {
        var normalized = value == null ? null : Schema.normalize(prop, value);
        if (normalized == null) {
            if (value != null) gui.notice(player, "invalid", Map.of("fields", ColorUtil.plain(prop.label())));
            gui.openSection(player, target, section);
            return;
        }
        target.save().apply(Map.of(prop.id(), normalized))
            .whenComplete((invalid, error) -> gui.hop(player, () -> {
                if (error != null) gui.notice(player, "not-found", Map.of("id", target.placeholders().getOrDefault("id", "")));
                else gui.notice(player, invalid.isEmpty() ? "saved" : "invalid", Map.of("fields", ColorUtil.plain(prop.label())));
                gui.openSection(player, target, section);
            }));
    }
}
