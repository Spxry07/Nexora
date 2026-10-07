package net.spxry.nexora.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.config.MessageManager;
import net.spxry.nexora.util.ColorUtil;
import net.spxry.nexora.util.ResourceFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuiConfig {
    public static final String COMMON = "common";
    private static final String PATH_FORMAT = "gui/%s.yml";
    private static final List<String> FILES = List.of(COMMON, "main", "list", "editor", "props", "lines", "line-editor", "equipment", "picker", "triggers", "confirm");
    private static final String ITEMS = "items.";
    private static final String FILLER = "filler";
    private static final String FIELD_SLOT = "slot";
    private static final String FIELD_MATERIAL = "material";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_LORE = "lore";
    private static final String FIELD_GLOW = "glow";
    private static final String LINE_BREAK = "\n";
    private static final char RANGE_SEPARATOR = '-';
    private static final int NO_SLOT = -1;
    private static final Material FALLBACK_MATERIAL = Material.PAPER;
    private static final YamlConfiguration EMPTY = new YamlConfiguration();

    private final Nexora plugin;
    private volatile Map<String, YamlConfiguration> files = Map.of();

    public GuiConfig(Nexora plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        Map<String, YamlConfiguration> loaded = new HashMap<>();
        for (var name : FILES) loaded.put(name, ResourceFiles.merged(plugin, PATH_FORMAT.formatted(name)));
        files = Map.copyOf(loaded);
    }

    public YamlConfiguration file(String name) {
        return files.getOrDefault(name, EMPTY);
    }

    public String string(String file, String path, String fallback) {
        return file(file).getString(path, fallback);
    }

    public int integer(String file, String path, int fallback) {
        return file(file).getInt(path, fallback);
    }

    public double decimal(String file, String path, double fallback) {
        return file(file).getDouble(path, fallback);
    }

    public boolean bool(String file, String path, boolean fallback) {
        return file(file).getBoolean(path, fallback);
    }

    public List<String> list(String file, String path) {
        return file(file).getStringList(path);
    }

    public ConfigurationSection section(String file, String path) {
        return file(file).getConfigurationSection(path);
    }

    public Component text(String file, String path, Map<String, String> ph) {
        return ColorUtil.colorize(MessageManager.apply(string(file, path, ""), ph));
    }

    public List<Integer> slots(String file, String path) {
        List<Integer> out = new ArrayList<>();
        for (var entry : file(file).getList(path, List.of())) {
            var text = String.valueOf(entry).trim();
            var dash = text.indexOf(RANGE_SEPARATOR, 1);
            try {
                if (dash < 0) {
                    out.add(Integer.parseInt(text));
                    continue;
                }
                var last = Integer.parseInt(text.substring(dash + 1).trim());
                for (int slot = Integer.parseInt(text.substring(0, dash).trim()); slot <= last; slot++) out.add(slot);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public int slot(String file, String key) {
        var owner = owner(file, key, FIELD_SLOT);
        return owner == null ? NO_SLOT : owner.getInt(FIELD_SLOT, NO_SLOT);
    }

    public ItemStack item(String file, String key, Map<String, String> ph) {
        var material = owner(file, key, FIELD_MATERIAL);
        var name = owner(file, key, FIELD_NAME);
        var lore = owner(file, key, FIELD_LORE);
        var glow = owner(file, key, FIELD_GLOW);
        if (material == null && name == null && lore == null) return null;
        return make(
            material == null ? "" : material.getString(FIELD_MATERIAL, ""),
            name == null ? "" : name.getString(FIELD_NAME, ""),
            lore == null ? List.of() : lore.getStringList(FIELD_LORE),
            glow != null && glow.getBoolean(FIELD_GLOW),
            ph);
    }

    public ItemStack filler() {
        var section = section(COMMON, FILLER);
        if (section == null) return make("", " ", List.of(), false, Map.of());
        return make(section.getString(FIELD_MATERIAL, ""), section.getString(FIELD_NAME, " "), section.getStringList(FIELD_LORE), false, Map.of());
    }

    public ItemStack make(String material, String name, List<String> lore, boolean glow, Map<String, String> ph) {
        var stack = ItemStack.of(material(MessageManager.apply(material, ph)));
        stack.editMeta(meta -> {
            if (!name.isEmpty()) meta.displayName(styled(MessageManager.apply(name, ph)));
            meta.lore(lines(lore, ph));
            meta.addItemFlags(ItemFlag.values());
            if (glow) meta.setEnchantmentGlintOverride(true);
        });
        return stack;
    }

    public Material material(String name) {
        var found = name == null ? null : Material.matchMaterial(name.trim());
        return found != null && found.isItem() && !found.isAir() ? found : FALLBACK_MATERIAL;
    }

    private ConfigurationSection owner(String file, String key, String field) {
        var primary = section(file, ITEMS + key);
        if (primary != null && primary.contains(field)) return primary;
        var shared = section(COMMON, ITEMS + key);
        return shared != null && shared.contains(field) ? shared : null;
    }

    private List<Component> lines(List<String> lore, Map<String, String> ph) {
        List<Component> out = new ArrayList<>();
        for (var raw : lore) {
            for (var part : MessageManager.apply(raw, ph).split(LINE_BREAK, -1)) out.add(styled(part));
        }
        return out;
    }

    private Component styled(String text) {
        return ColorUtil.colorize(text).decoration(TextDecoration.ITALIC, false);
    }
}
