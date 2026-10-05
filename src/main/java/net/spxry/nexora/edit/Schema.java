package net.spxry.nexora.edit;

import org.bukkit.configuration.ConfigurationSection;
import net.spxry.nexora.util.ResourceFiles;
import org.bukkit.plugin.Plugin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class Schema {
    public enum Type { TEXT, MULTILINE, NUMBER, INTEGER, BOOL, CHOICE, COLOR }

    public record Option(String id, String label) {}

    public record Prop(String id, String label, Type type, double min, double max, double step, List<Option> options) {}

    public record Section(String id, String title, List<Prop> props) {}

    private static final Pattern COLOR = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    private final Plugin plugin;
    private volatile Map<String, Map<String, Prop>> props = Map.of();
    private volatile Map<String, List<Section>> sections = Map.of();

    public Schema(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var yaml = ResourceFiles.versioned(plugin, "editor.yml");
        var kinds = yaml.getConfigurationSection("kinds");
        Map<String, Map<String, Prop>> newProps = new LinkedHashMap<>();
        Map<String, List<Section>> newSections = new LinkedHashMap<>();
        if (kinds != null) {
            for (var kind : kinds.getKeys(false)) {
                var kindProps = parseProps(kinds.getConfigurationSection(kind + ".properties"));
                newProps.put(kind, Map.copyOf(kindProps));
                newSections.put(kind, parseSections(kinds.getConfigurationSection(kind + ".sections"), kindProps));
            }
        }
        props = Map.copyOf(newProps);
        sections = Map.copyOf(newSections);
    }

    private Map<String, Prop> parseProps(ConfigurationSection root) {
        Map<String, Prop> out = new LinkedHashMap<>();
        if (root == null) return out;
        for (var id : root.getKeys(false)) {
            var s = root.getConfigurationSection(id);
            if (s == null) continue;
            Type type;
            try {
                type = Type.valueOf(s.getString("type", "text").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                continue;
            }
            List<Option> options = new ArrayList<>();
            var opts = s.getConfigurationSection("options");
            if (opts != null) for (var key : opts.getKeys(false)) options.add(new Option(key, opts.getString(key, key)));
            out.put(id, new Prop(id, s.getString("label", id), type, s.getDouble("min", 0), s.getDouble("max", 0), s.getDouble("step", 0), List.copyOf(options)));
        }
        return out;
    }

    private List<Section> parseSections(ConfigurationSection root, Map<String, Prop> kindProps) {
        List<Section> out = new ArrayList<>();
        if (root == null) return out;
        for (var id : root.getKeys(false)) {
            List<Prop> list = new ArrayList<>();
            for (var propId : root.getStringList(id + ".properties")) {
                var prop = kindProps.get(propId);
                if (prop != null) list.add(prop);
            }
            out.add(new Section(id, root.getString(id + ".title", id), List.copyOf(list)));
        }
        return List.copyOf(out);
    }

    public Map<String, Prop> props(String kind) { return props.getOrDefault(kind, Map.of()); }

    public List<Section> sections(String kind) { return sections.getOrDefault(kind, List.of()); }

    public java.util.Optional<Section> section(String kind, String id) {
        return sections(kind).stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public <T> Map<String, String> read(Map<String, Binding<T>> bindings, T target) {
        Map<String, String> out = new LinkedHashMap<>();
        bindings.forEach((id, binding) -> out.put(id, binding.getter().apply(target)));
        return out;
    }

    public <T> List<String> apply(String kind, Map<String, Binding<T>> bindings, T target, Map<String, String> values) {
        List<String> invalid = new ArrayList<>();
        var defs = props(kind);
        for (var entry : values.entrySet()) {
            var prop = defs.get(entry.getKey());
            var binding = bindings.get(entry.getKey());
            var normalized = prop == null || binding == null ? null : normalize(prop, entry.getValue());
            if (normalized == null) {
                invalid.add(entry.getKey());
                continue;
            }
            binding.setter().accept(target, normalized);
        }
        return invalid;
    }

    public static String normalize(Prop prop, String raw) {
        if (raw == null) return null;
        return switch (prop.type()) {
            case TEXT, MULTILINE -> prop.max() > 0 && raw.length() > prop.max() ? raw.substring(0, (int) prop.max()) : raw;
            case NUMBER, INTEGER -> {
                double v;
                try {
                    v = Double.parseDouble(raw.trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
                if (!Double.isFinite(v)) yield null;
                v = clamp(prop, v);
                if (prop.step() > 0) v = clamp(prop, Math.round(v / prop.step()) * prop.step());
                yield prop.type() == Type.INTEGER ? String.valueOf(Math.round(v)) : format(v);
            }
            case BOOL -> raw.trim().equalsIgnoreCase("true") ? "true" : raw.trim().equalsIgnoreCase("false") ? "false" : null;
            case CHOICE -> prop.options().stream().map(Option::id).filter(id -> id.equalsIgnoreCase(raw.trim())).findFirst().orElse(null);
            case COLOR -> {
                var m = COLOR.matcher(raw.trim());
                yield m.matches() ? "#" + m.group(1).toUpperCase(Locale.ROOT) : null;
            }
        };
    }

    public static double clamp(Prop prop, double v) {
        return prop.max() > prop.min() ? Math.max(prop.min(), Math.min(prop.max(), v)) : v;
    }

    public static String format(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
