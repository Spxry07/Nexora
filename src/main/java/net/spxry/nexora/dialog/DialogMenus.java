package net.spxry.nexora.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.edit.Schema;
import net.spxry.nexora.object.HoloLine;
import net.spxry.nexora.object.Hologram;
import net.spxry.nexora.object.NexoraObject;
import net.spxry.nexora.object.Npc;
import net.spxry.nexora.util.ColorUtil;
import net.spxry.nexora.util.ItemCodec;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class DialogMenus {
    private static final int ID_MAX_LENGTH = 32;
    private static final int COLOR_MAX_LENGTH = 9;
    private static final int TEXT_FALLBACK_LENGTH = 256;
    private static final String INPUT_ID = "id";
    private static final String INPUT_ENTITY_TYPE = "entity-type";
    private static final char KEY_DASH = '-';
    private static final char KEY_UNDERSCORE = '_';
    private static final String PROP_TYPE = "type";
    private static final String PROP_MATERIAL = "material";
    private static final String TYPE_ITEM = "ITEM";
    private static final String TYPE_BLOCK = "BLOCK";
    private static final String KINDS_KEY = "dialog.kinds.";
    private static final String BUTTON_KEY = "dialog.button.";
    private static final String TOOLTIP_KEY = "dialog.tooltip.";
    private static final String PREVIEW_KEY = "dialog.preview.";
    private static final String NOTICE_KEY = "dialog.notice.";
    private static final String NOT_FOUND_KEY = "cmd.not-found";

    private record Target(
        String kind,
        String titleKey,
        Map<String, String> placeholders,
        Supplier<CompletableFuture<Map<String, String>>> values,
        Function<Map<String, String>, CompletableFuture<List<String>>> save,
        Consumer<Player> back) {}

    private record LineInfo(int size, String preview) {}

    private final Nexora plugin;

    public DialogMenus(Nexora plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        actions.add(nav(player, "holograms", p -> openList(p, NexoraObject.HOLOGRAM)));
        actions.add(nav(player, "npcs", p -> openList(p, NexoraObject.NPC)));
        actions.add(nav(player, "create-hologram", p -> openCreate(p, NexoraObject.HOLOGRAM)));
        actions.add(nav(player, "create-npc", p -> openCreate(p, NexoraObject.NPC)));
        actions.add(nav(player, "demo-spawn", this::spawnDemo));
        actions.add(nav(player, "demo-clear", this::clearDemo));
        if (plugin.getConfig().getBoolean("web.enabled") && player.hasPermission(plugin.getConfig().getString("permissions.web", ""))) {
            actions.add(nav(player, "web", p -> plugin.web().sendLink(p)));
        }
        show(player, text("dialog.title.main"), null, List.of(text("dialog.body.main")), List.of(),
            DialogType.multiAction(actions, close(), columns()));
    }

    public void spawnDemo(Player player) {
        var ids = plugin.objects().spawnDemo(player);
        plugin.messages().send(player, "cmd.demo-spawned", Map.of("count", String.valueOf(ids.size()), "ids", String.join(", ", ids)));
    }

    public void clearDemo(Player player) {
        plugin.messages().send(player, "cmd.demo-cleared", Map.of("count", String.valueOf(plugin.objects().clearDemo())));
    }

    public void openList(Player player, String kind) {
        var objects = plugin.objects().list(kind);
        Map<String, String> ph = new HashMap<>();
        ph.put("kind", kindName(kind, "singular"));
        ph.put("kinds", kindName(kind, "plural"));
        ph.put("count", String.valueOf(objects.size()));
        List<ActionButton> actions = new ArrayList<>();
        for (var obj : objects) {
            actions.add(nav(player, text(BUTTON_KEY + "object", Map.of("id", obj.id())), text(TOOLTIP_KEY + "object", info(obj)), p -> openEditor(p, obj)));
        }
        actions.add(nav(player, text(BUTTON_KEY + "create", ph), tooltip("create"), p -> openCreate(p, kind)));
        var body = text(objects.isEmpty() ? "dialog.body.list-empty" : "dialog.body.list", ph);
        show(player, text("dialog.title.list", ph), null, List.of(body), List.of(),
            DialogType.multiAction(actions, nav(player, "back", this::openMain), columns()));
    }

    public void openCreate(Player player, String kind) {
        openCreate(player, kind, null);
    }

    public void openEditor(Player player, NexoraObject obj) {
        openEditor(player, obj, null);
    }

    public CompletableFuture<Boolean> addLine(Hologram holo, Map<String, String> props, String itemData) {
        var added = new AtomicBoolean();
        return plugin.objects().mutate(holo, h -> {
            if (h.lines().size() >= plugin.getConfig().getInt("limits.max-lines")) return;
            var line = plugin.objects().newLine();
            if (!props.isEmpty()) line.apply(plugin.schema(), props);
            if (itemData != null) line.itemData(itemData);
            h.lines().add(line);
            added.set(true);
        }).thenApply(v -> added.get());
    }

    public CompletableFuture<Void> setEquipment(Npc npc, EquipmentSlot slot, ItemStack item) {
        return plugin.objects().mutate(npc, n -> {
            if (item == null) n.equipment().remove(slot);
            else n.equipment().put(slot, item);
        });
    }

    public List<EquipmentSlot> slots() {
        List<EquipmentSlot> out = new ArrayList<>();
        for (var name : plugin.getConfig().getStringList("npc.equipment-slots")) {
            try {
                out.add(EquipmentSlot.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public String entityType(String candidate) {
        var prop = plugin.schema().props(NexoraObject.NPC).get(INPUT_ENTITY_TYPE);
        if (prop != null && candidate != null) {
            for (var option : prop.options()) if (option.id().equalsIgnoreCase(candidate.trim())) return option.id();
        }
        return plugin.getConfig().getString("npc.default-type", "");
    }

    private void openCreate(Player player, String kind, Component notice) {
        Map<String, String> ph = Map.of("kind", kindName(kind, "singular"));
        List<DialogInput> inputs = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        inputs.add(DialogInput.text(key(INPUT_ID), text("dialog.input.id")).maxLength(ID_MAX_LENGTH).width(inputWidth()).build());
        keys.add(INPUT_ID);
        if (NexoraObject.NPC.equals(kind)) {
            var prop = plugin.schema().props(NexoraObject.NPC).get(INPUT_ENTITY_TYPE);
            if (prop != null && !prop.options().isEmpty()) {
                inputs.add(choice(prop, plugin.getConfig().getString("npc.default-type", ""), ColorUtil.colorize(prop.label())));
                keys.add(INPUT_ENTITY_TYPE);
            }
        }
        var create = submit(player, "confirm-create", view -> readText(view, keys), (p, values) -> create(p, kind, values));
        var back = nav(player, "back", p -> openList(p, kind));
        show(player, text("dialog.title.create", ph), notice, List.of(text("dialog.body.create", ph)), inputs, DialogType.confirmation(create, back));
    }

    private void create(Player player, String kind, Map<String, String> values) {
        var id = values.getOrDefault(INPUT_ID, "").trim();
        var objects = plugin.objects();
        if (!objects.validId(id)) {
            openCreate(player, kind, text(NOTICE_KEY + "invalid-id", Map.of("id", id)));
            return;
        }
        if (objects.exists(kind, id)) {
            openCreate(player, kind, text(NOTICE_KEY + "exists", Map.of("id", id)));
            return;
        }
        var location = player.getLocation();
        NexoraObject created = NexoraObject.HOLOGRAM.equals(kind)
            ? objects.createHologram(id, location)
            : objects.createNpc(id, location, entityType(values.get(INPUT_ENTITY_TYPE)));
        plugin.messages().send(player, "cmd.created", Map.of("kind", kindName(kind, "singular"), "id", id));
        openEditor(player, created);
    }

    private void openEditor(Player player, NexoraObject obj, Component notice) {
        List<ActionButton> actions = new ArrayList<>();
        for (var section : plugin.schema().sections(obj.kind())) {
            actions.add(nav(player, ColorUtil.colorize(section.title()), null, p -> openSection(p, objectTarget(obj), section, null)));
        }
        if (obj instanceof Hologram holo) actions.add(nav(player, "lines", p -> openLines(p, holo, null)));
        if (obj instanceof Npc npc) {
            actions.add(nav(player, "equipment", p -> openEquipment(p, npc, null)));
            actions.add(nav(player, "nameplate", p -> openNameplate(p, npc)));
        }
        actions.add(nav(player, "move-here", p -> moveHere(p, obj)));
        actions.add(nav(player, "teleport", p -> teleport(p, obj)));
        actions.add(nav(player, "path-add", p -> addPoint(p, obj)));
        actions.add(nav(player, "path-clear", p -> clearPoints(p, obj)));
        actions.add(nav(player, "delete", p -> openDelete(p, obj)));
        var ph = info(obj);
        show(player, text("dialog.title.editor", ph), notice, List.of(text("dialog.body.editor", ph)), List.of(),
            DialogType.multiAction(actions, nav(player, "back", p -> openList(p, obj.kind())), columns()));
    }

    private void openNameplate(Player player, Npc npc) {
        query(player, npc, plugin.objects()::ensureNameplate, holo -> openLines(player, holo, null));
    }

    private void moveHere(Player player, NexoraObject obj) {
        var location = player.getLocation();
        mutate(player, obj, o -> o.moveTo(location), () -> openEditor(player, obj, text(NOTICE_KEY + "moved")));
    }

    private void teleport(Player player, NexoraObject obj) {
        var anchor = obj.anchor();
        if (anchor.getWorld() == null) {
            openEditor(player, obj, text(NOT_FOUND_KEY, Map.of("id", obj.id())));
            return;
        }
        player.teleportAsync(anchor);
        plugin.messages().send(player, "cmd.teleported", Map.of("id", obj.id()));
    }

    private void addPoint(Player player, NexoraObject obj) {
        if (obj.waypointCount() >= plugin.getConfig().getInt("limits.max-waypoints")) {
            openEditor(player, obj, text(NOTICE_KEY + "max-waypoints"));
            return;
        }
        var location = player.getLocation();
        mutate(player, obj, o -> o.addWaypoint(location),
            () -> openEditor(player, obj, text(NOTICE_KEY + "point-added", Map.of("count", String.valueOf(obj.waypointCount())))));
    }

    private void clearPoints(Player player, NexoraObject obj) {
        mutate(player, obj, NexoraObject::clearWaypoints, () -> openEditor(player, obj, text(NOTICE_KEY + "point-cleared")));
    }

    private void openDelete(Player player, NexoraObject obj) {
        Map<String, String> ph = Map.of("id", obj.id());
        var yes = nav(player, "delete-confirm", p -> {
            plugin.objects().delete(obj);
            plugin.messages().send(p, "cmd.deleted", ph);
            openList(p, obj.kind());
        });
        var no = nav(player, "delete-cancel", p -> openEditor(p, obj));
        show(player, text("dialog.title.delete", ph), null, List.of(text("dialog.body.delete", ph)), List.of(), DialogType.confirmation(yes, no));
    }

    private Target objectTarget(NexoraObject obj) {
        return new Target(obj.kind(), "dialog.title.section", Map.of("id", obj.id()),
            () -> plugin.objects().query(obj, NexoraObject::values),
            values -> {
                var invalid = new AtomicReference<List<String>>(List.of());
                return plugin.objects().mutate(obj, o -> invalid.set(o.apply(values))).thenApply(v -> invalid.get());
            },
            p -> openEditor(p, obj, null));
    }

    private Target lineTarget(Hologram holo, int index) {
        return new Target(NexoraObject.LINE, "dialog.title.line-section", Map.of("id", holo.id(), "index", String.valueOf(index + 1)),
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
            p -> openLine(p, holo, index, null));
    }

    private void openSection(Player player, Target target, Schema.Section section, Component notice) {
        target.values().get()
            .thenAccept(values -> hop(player, () -> renderSection(player, target, section, notice, values)))
            .exceptionally(ex -> fail(player, target.placeholders().get("id"), ex));
    }

    private void renderSection(Player player, Target target, Schema.Section section, Component notice, Map<String, String> values) {
        if (values == null) {
            target.back().accept(player);
            return;
        }
        List<DialogInput> inputs = new ArrayList<>();
        for (var prop : section.props()) inputs.add(input(prop, values.get(prop.id())));
        Map<String, String> ph = new HashMap<>(target.placeholders());
        ph.put("section", section.title());
        var save = submit(player, "save", view -> readProps(view, section.props()), (p, map) -> save(p, target, section, map));
        var back = nav(player, "back", target.back());
        show(player, text(target.titleKey(), ph), notice, List.of(), inputs, DialogType.confirmation(save, back));
    }

    private void save(Player player, Target target, Schema.Section section, Map<String, String> values) {
        target.save().apply(values)
            .thenAccept(invalid -> hop(player, () -> openSection(player, target, section, saveNotice(target.kind(), invalid))))
            .exceptionally(ex -> fail(player, target.placeholders().get("id"), ex));
    }

    private Component saveNotice(String kind, List<String> invalid) {
        if (invalid.isEmpty()) return text(NOTICE_KEY + "saved");
        var props = plugin.schema().props(kind);
        List<String> names = new ArrayList<>();
        for (var id : invalid) names.add(props.containsKey(id) ? ColorUtil.plain(props.get(id).label()) : id);
        return text(NOTICE_KEY + "invalid", Map.of("fields", String.join(", ", names)));
    }

    private DialogInput input(Schema.Prop prop, String current) {
        var label = ColorUtil.colorize(prop.label());
        var value = current == null ? "" : current;
        return switch (prop.type()) {
            case BOOL -> DialogInput.bool(key(prop.id()), label).initial(Boolean.parseBoolean(value)).build();
            case CHOICE -> prop.options().isEmpty() ? textInput(prop, value, label, false) : choice(prop, value, label);
            case NUMBER, INTEGER -> prop.max() > prop.min() ? slider(prop, value, label) : textInput(prop, value, label, false);
            case MULTILINE -> textInput(prop, value, label, true);
            case TEXT, COLOR -> textInput(prop, value, label, false);
        };
    }

    private DialogInput slider(Schema.Prop prop, String value, Component label) {
        var min = (float) prop.min();
        var max = (float) prop.max();
        var initial = (float) Math.max(min, Math.min(max, parse(value, prop.min())));
        var builder = DialogInput.numberRange(key(prop.id()), label, min, max).initial(initial).width(inputWidth());
        if (prop.step() > 0) builder = builder.step((float) prop.step());
        return builder.build();
    }

    private DialogInput textInput(Schema.Prop prop, String value, Component label, boolean multiline) {
        var declared = prop.max() > 0 ? (int) prop.max() : prop.type() == Schema.Type.COLOR ? COLOR_MAX_LENGTH : TEXT_FALLBACK_LENGTH;
        var builder = DialogInput.text(key(prop.id()), label).initial(value).maxLength(Math.max(declared, value.length())).width(inputWidth());
        if (multiline) {
            builder = builder.multiline(TextDialogInput.MultilineOptions.create(positive("dialogs.multiline-max-lines"), positive("dialogs.multiline-height")));
        }
        return builder.build();
    }

    private DialogInput choice(Schema.Prop prop, String current, Component label) {
        var options = prop.options();
        var matched = options.stream().anyMatch(o -> o.id().equalsIgnoreCase(current));
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            var option = options.get(i);
            var initial = matched ? option.id().equalsIgnoreCase(current) : i == 0;
            entries.add(SingleOptionDialogInput.OptionEntry.create(option.id(), ColorUtil.colorize(option.label()), initial));
        }
        return DialogInput.singleOption(key(prop.id()), label, entries).width(inputWidth()).build();
    }

    private Map<String, String> readText(DialogResponseView view, List<String> keys) {
        Map<String, String> out = new LinkedHashMap<>();
        if (view == null) return out;
        for (var key : keys) {
            var value = view.getText(key(key));
            if (value != null) out.put(key, value);
        }
        return out;
    }

    private Map<String, String> readProps(DialogResponseView view, List<Schema.Prop> props) {
        Map<String, String> out = new LinkedHashMap<>();
        if (view == null) return out;
        for (var prop : props) {
            var id = prop.id();
            var value = switch (prop.type()) {
                case BOOL -> {
                    var flag = view.getBoolean(key(id));
                    yield flag == null ? null : String.valueOf(flag);
                }
                case NUMBER, INTEGER -> {
                    if (prop.max() > prop.min()) {
                        var number = view.getFloat(key(id));
                        yield number == null ? null : Schema.format(number);
                    }
                    yield view.getText(key(id));
                }
                default -> view.getText(key(id));
            };
            if (value != null) out.put(id, value);
        }
        return out;
    }

    private void openLines(Player player, Hologram holo, Component notice) {
        query(player, holo, this::previews, previews -> {
            List<ActionButton> actions = new ArrayList<>();
            for (int i = 0; i < previews.size(); i++) {
                var index = i;
                var label = text(BUTTON_KEY + "line", Map.of("index", String.valueOf(i + 1), "preview", previews.get(i)));
                actions.add(nav(player, label, tooltip("line"), p -> openLine(p, holo, index, null)));
            }
            actions.add(nav(player, "add-text", p -> addLine(p, holo, Map.of(), null)));
            actions.add(nav(player, "add-item", p -> addHeld(p, holo, false)));
            actions.add(nav(player, "add-block", p -> addHeld(p, holo, true)));
            Map<String, String> ph = Map.of("id", holo.id(), "count", String.valueOf(previews.size()), "max", String.valueOf(plugin.getConfig().getInt("limits.max-lines")));
            show(player, text("dialog.title.lines", ph), notice, List.of(text("dialog.body.lines", ph)), List.of(),
                DialogType.multiAction(actions, nav(player, "back", p -> openEditor(p, holo, null)), columns()));
        });
    }

    private List<String> previews(Hologram holo) {
        List<String> out = new ArrayList<>();
        for (var line : holo.lines()) out.add(preview(line));
        return out;
    }

    private String preview(HoloLine line) {
        var type = line.type();
        if (TYPE_ITEM.equalsIgnoreCase(type) || TYPE_BLOCK.equalsIgnoreCase(type)) {
            return plugin.messages().raw(PREVIEW_KEY + type.toLowerCase(Locale.ROOT));
        }
        var text = ColorUtil.plain(line.text()).replace('\n', ' ').trim();
        if (text.isEmpty()) return plugin.messages().raw(PREVIEW_KEY + "empty");
        var limit = positive("dialogs.preview-length");
        return text.length() > limit ? text.substring(0, limit) + plugin.messages().raw(PREVIEW_KEY + "ellipsis") : text;
    }

    private void addLine(Player player, Hologram holo, Map<String, String> props, String itemData) {
        addLine(holo, props, itemData)
            .thenAccept(added -> hop(player, () -> openLines(player, holo, added
                ? text(NOTICE_KEY + "line-added")
                : text(NOTICE_KEY + "line-max", Map.of("max", String.valueOf(plugin.getConfig().getInt("limits.max-lines")))))))
            .exceptionally(ex -> fail(player, holo.id(), ex));
    }

    private void addHeld(Player player, Hologram holo, boolean block) {
        var item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            openLines(player, holo, text(NOTICE_KEY + "no-item"));
            return;
        }
        if (block) {
            if (!item.getType().isBlock()) {
                openLines(player, holo, text(NOTICE_KEY + "no-block"));
                return;
            }
            addLine(player, holo, Map.of(PROP_TYPE, TYPE_BLOCK, PROP_MATERIAL, item.getType().name()), null);
            return;
        }
        addLine(player, holo, Map.of(PROP_TYPE, TYPE_ITEM), ItemCodec.encode(item.asOne()));
    }

    private void openLine(Player player, Hologram holo, int index, Component notice) {
        query(player, holo, h -> {
            var lines = h.lines();
            return index < lines.size() ? new LineInfo(lines.size(), preview(lines.get(index))) : null;
        }, line -> {
            if (line == null) {
                openLines(player, holo, null);
                return;
            }
            List<ActionButton> actions = new ArrayList<>();
            for (var section : plugin.schema().sections(NexoraObject.LINE)) {
                actions.add(nav(player, ColorUtil.colorize(section.title()), null, p -> openSection(p, lineTarget(holo, index), section, null)));
            }
            if (index > 0) actions.add(nav(player, "line-up", p -> moveLine(p, holo, index, -1)));
            if (index < line.size() - 1) actions.add(nav(player, "line-down", p -> moveLine(p, holo, index, 1)));
            actions.add(nav(player, "line-delete", p -> deleteLine(p, holo, index)));
            Map<String, String> ph = Map.of("id", holo.id(), "index", String.valueOf(index + 1), "size", String.valueOf(line.size()), "preview", line.preview());
            show(player, text("dialog.title.line", ph), notice, List.of(text("dialog.body.line", ph)), List.of(),
                DialogType.multiAction(actions, nav(player, "back", p -> openLines(p, holo, null)), columns()));
        });
    }

    private void moveLine(Player player, Hologram holo, int index, int delta) {
        var target = index + delta;
        mutate(player, holo, h -> {
            var lines = h.lines();
            if (index < 0 || index >= lines.size() || target < 0 || target >= lines.size()) return;
            var moved = lines.get(index);
            lines.set(index, lines.get(target));
            lines.set(target, moved);
        }, () -> openLine(player, holo, target, null));
    }

    private void deleteLine(Player player, Hologram holo, int index) {
        mutate(player, holo, h -> {
            if (index >= 0 && index < h.lines().size()) h.lines().remove(index);
        }, () -> openLines(player, holo, text(NOTICE_KEY + "line-deleted")));
    }

    private void openEquipment(Player player, Npc npc, Component notice) {
        query(player, npc, n -> new HashMap<>(n.equipment()), worn -> {
            List<Component> body = new ArrayList<>();
            body.add(text("dialog.body.equipment"));
            List<ActionButton> actions = new ArrayList<>();
            for (var slot : slots()) {
                Map<String, String> ph = Map.of("slot", slot.name());
                var stack = worn.get(slot);
                var item = stack == null || stack.isEmpty() ? plugin.messages().raw("dialog.empty-slot") : stack.getType().name();
                body.add(text("dialog.body.equipment-slot", Map.of("slot", slot.name(), "item", item)));
                actions.add(nav(player, text(BUTTON_KEY + "equip-set", ph), tooltip("equip-set"), p -> equip(p, npc, slot, true)));
                actions.add(nav(player, text(BUTTON_KEY + "equip-clear", ph), tooltip("equip-clear"), p -> equip(p, npc, slot, false)));
            }
            show(player, text("dialog.title.equipment", Map.of("id", npc.id())), notice, body, List.of(),
                DialogType.multiAction(actions, nav(player, "back", p -> openEditor(p, npc, null)), columns()));
        });
    }

    private void equip(Player player, Npc npc, EquipmentSlot slot, boolean fromHand) {
        var held = player.getInventory().getItemInMainHand();
        if (fromHand && held.isEmpty()) {
            openEquipment(player, npc, text(NOTICE_KEY + "no-item"));
            return;
        }
        var key = NOTICE_KEY + (fromHand ? "equipped" : "unequipped");
        setEquipment(npc, slot, fromHand ? held.asOne() : null)
            .thenRun(() -> hop(player, () -> openEquipment(player, npc, text(key, Map.of("slot", slot.name())))))
            .exceptionally(ex -> fail(player, npc.id(), ex));
    }

    private <T extends NexoraObject> void mutate(Player player, T obj, Consumer<T> change, Runnable then) {
        plugin.objects().mutate(obj, change)
            .thenRun(() -> hop(player, then))
            .exceptionally(ex -> fail(player, obj.id(), ex));
    }

    private <T extends NexoraObject, R> void query(Player player, T obj, Function<T, R> fn, Consumer<R> then) {
        plugin.objects().query(obj, fn)
            .thenAccept(result -> hop(player, () -> then.accept(result)))
            .exceptionally(ex -> fail(player, obj.id(), ex));
    }

    private Void fail(Player player, String id, Throwable ex) {
        plugin.getLogger().log(Level.FINE, id, ex);
        hop(player, () -> plugin.messages().send(player, NOT_FOUND_KEY, Map.of("id", String.valueOf(id))));
        return null;
    }

    private void hop(Player player, Runnable task) {
        plugin.scheduler().runAtEntity(player, task);
    }

    private ActionButton nav(Player player, String key, Consumer<Player> click) {
        return nav(player, text(BUTTON_KEY + key), tooltip(key), click);
    }

    private ActionButton nav(Player player, Component label, Component tooltip, Consumer<Player> click) {
        var action = DialogAction.customClick((view, audience) -> hop(player, () -> click.accept(player)), callbackOptions());
        return button(label, tooltip, action);
    }

    private ActionButton submit(Player player, String key, Function<DialogResponseView, Map<String, String>> reader, BiConsumer<Player, Map<String, String>> handler) {
        var action = DialogAction.customClick((view, audience) -> {
            var values = reader.apply(view);
            hop(player, () -> handler.accept(player, values));
        }, callbackOptions());
        return button(text(BUTTON_KEY + key), tooltip(key), action);
    }

    private ActionButton close() {
        return button(text(BUTTON_KEY + "close"), tooltip("close"), null);
    }

    private ActionButton button(Component label, Component tooltip, DialogAction action) {
        var builder = ActionButton.builder(label).width(positive("dialogs.button-width"));
        if (tooltip != null) builder = builder.tooltip(tooltip);
        if (action != null) builder = builder.action(action);
        return builder.build();
    }

    private ClickCallback.Options callbackOptions() {
        return ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(positive("dialogs.callback-lifetime-minutes")))
            .build();
    }

    private void show(Player player, Component title, Component notice, List<Component> lines, List<DialogInput> inputs, DialogType type) {
        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(DialogBody.plainMessage(notice));
        for (var line : lines) body.add(DialogBody.plainMessage(line));
        var base = DialogBase.builder(title).body(body).inputs(inputs).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    private Map<String, String> info(NexoraObject obj) {
        var world = obj.anchor().getWorld();
        Map<String, String> ph = new HashMap<>();
        ph.put("id", obj.id());
        ph.put("kind", kindName(obj.kind(), "singular"));
        ph.put("world", world == null ? "" : world.getName());
        ph.put("x", Schema.format(obj.x()));
        ph.put("y", Schema.format(obj.y()));
        ph.put("z", Schema.format(obj.z()));
        ph.put("points", String.valueOf(obj.waypointCount()));
        return ph;
    }

    private String kindName(String kind, String form) {
        return plugin.messages().raw(KINDS_KEY + kind + "." + form);
    }

    private Component text(String key) {
        return plugin.messages().component(key);
    }

    private Component text(String key, Map<String, String> ph) {
        return plugin.messages().component(key, ph);
    }

    private Component tooltip(String key) {
        var full = TOOLTIP_KEY + key;
        return plugin.messages().has(full) ? text(full) : null;
    }

    private double parse(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int positive(String path) {
        return Math.max(1, plugin.getConfig().getInt(path));
    }

    private static String key(String id) {
        return id.replace(KEY_DASH, KEY_UNDERSCORE);
    }

    private int columns() {
        return positive("dialogs.columns");
    }

    private int inputWidth() {
        return positive("dialogs.input-width");
    }
}
