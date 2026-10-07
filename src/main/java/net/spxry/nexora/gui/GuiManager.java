package net.spxry.nexora.gui;

import net.spxry.nexora.Nexora;
import net.spxry.nexora.config.MessageManager;
import net.spxry.nexora.edit.Schema;
import net.spxry.nexora.object.HoloLine;
import net.spxry.nexora.object.Hologram;
import net.spxry.nexora.object.NexoraObject;
import net.spxry.nexora.object.Npc;
import net.spxry.nexora.util.ColorUtil;
import net.spxry.nexora.util.ItemCodec;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public final class GuiManager {
    private static final String COMMON = GuiConfig.COMMON;
    private static final String MAIN = "main";
    private static final String LIST = "list";
    private static final String LINES = "lines";
    private static final String NOTICES = "notices.";
    private static final String VALUES = "values.";
    private static final String SOUNDS = "sounds.";
    private static final String SINGULAR = "singular";
    private static final String KINDS_KEY = "dialog.kinds.";
    private static final String ENTITY_TYPE = "entity-type";
    private static final String ENTITY_PICKER = "entity-type";
    private static final String TRIGGERS_PROP = "triggers";
    private static final String LAYOUT_PROP = "layout";
    private static final String TYPE_PROP = "type";
    private static final String MATERIAL_PROP = "material";
    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_ITEM = "ITEM";
    private static final String TYPE_BLOCK = "BLOCK";
    private static final String SAVED = "saved";
    private static final String INVALID = "invalid";
    private static final String NOT_FOUND = "not-found";
    private static final int DEFAULT_PREVIEW = 28;
    private static final int DEFAULT_ID_LENGTH = 32;
    private static final double DEFAULT_VOLUME = 1.0;
    private static final double DEFAULT_PITCH = 1.0;

    private final Nexora plugin;
    private final GuiConfig config;

    public GuiManager(Nexora plugin) {
        this.plugin = plugin;
        this.config = new GuiConfig(plugin);
        plugin.getServer().getPluginManager().registerEvents(new GuiListener(), plugin);
    }

    public void reload() {
        config.reload();
    }

    public void openMain(Player player) {
        show(new MainGui(this, player));
    }

    public void openEditor(Player player, NexoraObject object) {
        if (object instanceof Hologram holo) show(new HologramEditorGui(this, player, holo));
        else if (object instanceof Npc npc) show(new NpcEditorGui(this, player, npc));
    }

    public void openList(Player player, String kind) {
        openList(player, kind, 0, "");
    }

    Nexora plugin() {
        return plugin;
    }

    GuiConfig config() {
        return config;
    }

    void openList(Player player, String kind, int page, String query) {
        show(new ListGui(this, player, kind, page, query));
    }

    void show(NexoraGui gui) {
        plugin.scheduler().runAtEntity(gui.player(), gui::open);
    }

    void hop(Player player, Runnable task) {
        plugin.scheduler().runAtEntity(player, task);
    }

    void sound(Player player, String key) {
        if (key == null || key.isEmpty() || !config.bool(COMMON, SOUNDS + "enabled", true)) return;
        var path = SOUNDS + key;
        MessageManager.play(player,
            config.string(COMMON, path + ".name", ""),
            (float) config.decimal(COMMON, path + ".volume", DEFAULT_VOLUME),
            (float) config.decimal(COMMON, path + ".pitch", DEFAULT_PITCH));
    }

    void notice(Player player, String key, Map<String, String> ph) {
        var path = NOTICES + key;
        player.sendActionBar(config.text(COMMON, path + ".text", ph));
        sound(player, config.string(COMMON, path + ".sound", ""));
    }

    <T extends NexoraObject, R> void load(Player player, T obj, Function<T, R> fn, Consumer<R> then) {
        plugin.objects().query(obj, fn)
            .thenAccept(result -> hop(player, () -> then.accept(result)))
            .exceptionally(ex -> fail(player, obj.id(), ex));
    }

    <T extends NexoraObject> void mutate(Player player, T obj, Consumer<T> change, Runnable then) {
        plugin.objects().mutate(obj, change)
            .thenRun(() -> hop(player, then))
            .exceptionally(ex -> fail(player, obj.id(), ex));
    }

    Void fail(Player player, String id, Throwable ex) {
        plugin.getLogger().log(Level.FINE, id, ex);
        hop(player, () -> notice(player, NOT_FOUND, Map.of("id", String.valueOf(id))));
        return null;
    }

    String kindName(String kind, String form) {
        return plugin.messages().raw(KINDS_KEY + kind + "." + form);
    }

    String value(String key) {
        return config.string(COMMON, VALUES + key, "");
    }

    String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + value("ellipsis") : text;
    }

    String preview(String raw) {
        var text = ColorUtil.plain(raw == null ? "" : raw).replace('\n', ' ').trim();
        if (text.isEmpty()) return value("empty");
        return truncate(text, config.integer(COMMON, "preview-length", DEFAULT_PREVIEW));
    }

    String optionLabel(String kind, String propId, String optionId) {
        if (optionId == null) return "";
        var prop = plugin.schema().props(kind).get(propId);
        if (prop == null) return optionId;
        for (var option : prop.options()) if (option.id().equalsIgnoreCase(optionId)) return option.label();
        return optionId;
    }

    boolean webAvailable(Player player) {
        var settings = plugin.getConfig();
        return settings.getBoolean("web.enabled") && player.hasPermission(settings.getString("permissions.web", ""));
    }

    Map<String, String> placeholders(NexoraObject obj) {
        var world = obj.anchor().getWorld();
        Map<String, String> ph = new HashMap<>();
        ph.put("id", obj.id());
        ph.put("kind", kindName(obj.kind(), SINGULAR));
        ph.put("world", world == null ? "" : world.getName());
        ph.put("x", Schema.format(obj.x()));
        ph.put("y", Schema.format(obj.y()));
        ph.put("z", Schema.format(obj.z()));
        ph.put("points", String.valueOf(obj.waypointCount()));
        ph.put("icon", iconMaterial(obj));
        if (obj instanceof Hologram holo) {
            ph.put("lines", String.valueOf(holo.lines().size()));
            ph.put("layout", optionLabel(NexoraObject.HOLOGRAM, LAYOUT_PROP, holo.values().get(LAYOUT_PROP)));
        }
        if (obj instanceof Npc npc) ph.put("type", optionLabel(NexoraObject.NPC, ENTITY_TYPE, npc.entityType()));
        return ph;
    }

    boolean isHeadType(String type) {
        return type != null && config.list(LIST, "head-types").stream().anyMatch(candidate -> candidate.equalsIgnoreCase(type.trim()));
    }

    String eggMaterial(String type) {
        var key = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        var override = config.string(LIST, "egg-overrides." + key, "");
        if (!override.isEmpty()) return override;
        var egg = key + config.string(LIST, "egg-suffix", "");
        return Material.matchMaterial(egg) == null ? config.string(LIST, "icons.npc-fallback", "") : egg;
    }

    String iconMaterial(NexoraObject obj) {
        if (obj instanceof Npc npc) {
            return isHeadType(npc.entityType()) ? config.string(LIST, "icons.head", "") : eggMaterial(npc.entityType());
        }
        return config.string(LIST, "icons.hologram", "");
    }

    ItemStack decorate(ItemStack stack, NexoraObject obj) {
        if (stack != null && obj instanceof Npc npc && isHeadType(npc.entityType())) Heads.apply(config, stack, npc.skinId());
        return stack;
    }

    PropTarget objectTarget(NexoraObject obj) {
        return new PropTarget(obj.kind(), Map.of("id", obj.id(), "suffix", ""),
            () -> plugin.objects().query(obj, NexoraObject::values),
            values -> {
                var invalid = new AtomicReference<List<String>>(List.of());
                return plugin.objects().mutate(obj, o -> invalid.set(o.apply(values))).thenApply(v -> invalid.get());
            },
            player -> openEditor(player, obj));
    }

    PropTarget lineTarget(Hologram holo, int index) {
        return new PropTarget(NexoraObject.LINE, Map.of("id", holo.id(), "suffix", " #" + (index + 1)),
            () -> plugin.objects().query(holo, h -> {
                var lines = h.lines();
                return index < lines.size() ? lines.get(index).values(plugin.schema()) : null;
            }),
            values -> {
                var invalid = new AtomicReference<List<String>>(List.of());
                return plugin.objects().mutate(holo, h -> {
                    var lines = h.lines();
                    if (index < lines.size()) invalid.set(lines.get(index).apply(plugin.schema(), values));
                }).thenApply(v -> invalid.get());
            },
            player -> openLineEditor(player, holo, index));
    }

    void openSection(Player player, PropTarget target, Schema.Section section) {
        target.values().get()
            .thenAccept(values -> hop(player, () -> {
                if (values == null) target.back().accept(player);
                else show(new PropGui(this, player, target, section, values));
            }))
            .exceptionally(ex -> fail(player, target.placeholders().get("id"), ex));
    }

    void openPropPicker(Player player, PropTarget target, String propId, String pickerKey, Consumer<Player> back) {
        var prop = plugin.schema().props(target.kind()).get(propId);
        if (prop == null) {
            back.accept(player);
            return;
        }
        target.values().get()
            .thenAccept(values -> hop(player, () -> {
                if (values == null) {
                    target.back().accept(player);
                    return;
                }
                Map<String, String> ph = new HashMap<>(target.placeholders());
                ph.put("prop", ColorUtil.plain(prop.label()));
                show(new PickerGui(this, player, pickerKey, ph, prop.options(), values.getOrDefault(propId, ""),
                    choice -> pick(player, target, propId, choice, back), back));
            }))
            .exceptionally(ex -> fail(player, target.placeholders().get("id"), ex));
    }

    private void pick(Player player, PropTarget target, String propId, String choice, Consumer<Player> back) {
        target.save().apply(Map.of(propId, choice))
            .thenAccept(invalid -> hop(player, () -> {
                notice(player, invalid.isEmpty() ? SAVED : INVALID, Map.of("fields", propId));
                back.accept(player);
            }))
            .exceptionally(ex -> fail(player, target.placeholders().get("id"), ex));
    }

    void startCreate(Player player, String kind) {
        var prop = plugin.schema().props(NexoraObject.NPC).get(ENTITY_TYPE);
        if (!NexoraObject.NPC.equals(kind) || prop == null || prop.options().isEmpty()) {
            promptId(player, kind, "", null, "");
            return;
        }
        show(new PickerGui(this, player, ENTITY_PICKER, Map.of(), prop.options(), plugin.getConfig().getString("npc.default-type", ""),
            type -> promptId(player, kind, type, null, ""), this::openMain));
    }

    private void promptId(Player player, String kind, String type, String errorKey, String errorId) {
        Map<String, String> ph = Map.of("kind", kindName(kind, SINGULAR));
        var body = errorKey == null
            ? config.text(MAIN, "prompt.create-body", ph)
            : config.text(COMMON, NOTICES + errorKey + ".text", Map.of("id", errorId));
        var spec = new TextPrompt.Spec(config.text(MAIN, "prompt.create-title", ph), body, config.text(MAIN, "prompt.create-label", ph),
            "", config.integer(COMMON, "prompt.id-max-length", DEFAULT_ID_LENGTH), false);
        prompt(player, spec, text -> create(player, kind, type, text), () -> openMain(player));
    }

    private void create(Player player, String kind, String type, String text) {
        if (text == null) {
            openMain(player);
            return;
        }
        var id = text.trim();
        var objects = plugin.objects();
        if (!objects.validId(id)) {
            promptId(player, kind, type, "invalid-id", id);
            return;
        }
        if (objects.exists(kind, id)) {
            promptId(player, kind, type, "exists", id);
            return;
        }
        var location = player.getLocation();
        NexoraObject created = NexoraObject.HOLOGRAM.equals(kind)
            ? objects.createHologram(id, location)
            : objects.createNpc(id, location, plugin.menus().entityType(type));
        plugin.messages().send(player, "cmd.created", Map.of("kind", kindName(kind, SINGULAR), "id", id));
        openEditor(player, created);
    }

    void prompt(Player player, TextPrompt.Spec spec, Consumer<String> submit, Runnable cancel) {
        TextPrompt.open(this, player, spec, submit, cancel);
    }

    void confirmDelete(Player player, NexoraObject obj, Consumer<Player> cancel) {
        show(new ConfirmGui(this, player, placeholders(obj), () -> {
            plugin.objects().delete(obj);
            sound(player, "delete");
            plugin.messages().send(player, "cmd.deleted", Map.of("id", obj.id()));
            openList(player, obj.kind(), 0, "");
        }, () -> cancel.accept(player)));
    }

    void teleport(Player player, NexoraObject obj) {
        var anchor = obj.anchor();
        if (anchor.getWorld() == null) {
            notice(player, NOT_FOUND, Map.of("id", obj.id()));
            return;
        }
        player.closeInventory();
        player.teleportAsync(anchor);
        plugin.messages().send(player, "cmd.teleported", Map.of("id", obj.id()));
    }

    void openTriggers(Player player, NexoraObject obj) {
        load(player, obj, o -> o.values().getOrDefault(TRIGGERS_PROP, ""), text -> show(new TriggersGui(this, player, obj, text)));
    }

    void openEquipment(Player player, Npc npc) {
        load(player, npc, this::copyEquipment, worn -> show(new EquipmentGui(this, player, npc, worn)));
    }

    void openLines(Player player, Hologram holo) {
        load(player, holo, this::lineViews, views -> show(new LinesGui(this, player, holo, views)));
    }

    void openLineEditor(Player player, Hologram holo, int index) {
        load(player, holo, h -> {
            var views = lineViews(h);
            return index >= 0 && index < views.size() ? views.get(index) : null;
        }, view -> {
            if (view == null) openLines(player, holo);
            else show(new LineEditorGui(this, player, holo, view));
        });
    }

    private Map<EquipmentSlot, ItemStack> copyEquipment(Npc npc) {
        Map<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        npc.equipment().forEach((slot, item) -> {
            if (item != null && !item.isEmpty()) copy.put(slot, item.clone());
        });
        return copy;
    }

    List<LineView> lineViews(Hologram holo) {
        var lines = List.copyOf(holo.lines());
        List<LineView> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) out.add(lineView(lines.get(i), i, lines.size()));
        return out;
    }

    private LineView lineView(HoloLine line, int index, int size) {
        var type = line.type() == null || line.type().isBlank() ? TYPE_TEXT : line.type().toUpperCase(Locale.ROOT);
        String material;
        String preview;
        switch (type) {
            case TYPE_ITEM -> {
                material = line.itemData() == null ? "" : ItemCodec.decode(line.itemData()).map(stack -> stack.getType().name()).orElse("");
                preview = value("item") + " " + material;
            }
            case TYPE_BLOCK -> {
                material = line.values(plugin.schema()).getOrDefault(MATERIAL_PROP, "");
                preview = value("block") + " " + material;
            }
            default -> {
                material = "";
                preview = preview(line.text());
            }
        }
        var icon = material.isEmpty() ? config.string(LINES, "icons." + type, "") : material;
        return new LineView(index, size, optionLabel(NexoraObject.LINE, TYPE_PROP, type), preview, icon);
    }
}
