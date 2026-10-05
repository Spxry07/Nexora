package net.spxry.nexora.command;

import net.spxry.nexora.Nexora;
import net.spxry.nexora.object.Hologram;
import net.spxry.nexora.object.NexoraObject;
import net.spxry.nexora.object.Npc;
import net.spxry.nexora.util.ItemCodec;
import net.spxry.nexora.web.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class NexoraCommand implements CommandExecutor, TabCompleter {
    private static final String HELP = "help";
    private static final String RELOAD = "reload";
    private static final String WEB = "web";
    private static final String HOLO = "holo";
    private static final String NPC = "npc";
    private static final String CREATE = "create";
    private static final String EDIT = "edit";
    private static final String DELETE = "delete";
    private static final String MOVE_HERE = "movehere";
    private static final String TP = "tp";
    private static final String LIST = "list";
    private static final String PATH = "path";
    private static final String ADD_LINE = "addline";
    private static final String ADD_ITEM = "additem";
    private static final String EQUIP = "equip";
    private static final String SKIN = "skin";
    private static final String DEMO = "demo";
    private static final String MINESKIN = "mineskin";
    private static final String MINESKIN_KEY_PATH = "model.mineskin.api-key";
    private static final String ENTITY_TYPE = "entity-type";
    private static final String ADD = "add";
    private static final String CLEAR = "clear";
    private static final String TEXTURES = "textures";
    private static final String SKIN_NAME = "skin-name";
    private static final String SKIN_VALUE = "skin-value";
    private static final String SKIN_SIGNATURE = "skin-signature";
    private static final String PROP_TYPE = "type";
    private static final String PROP_TEXT = "text";
    private static final String TYPE_ITEM = "ITEM";
    private static final String NEWLINE_ESCAPE = "\\n";
    private static final String NEWLINE = "\n";
    private static final String KINDS_KEY = "dialog.kinds.";
    private static final List<String> ROOT = List.of(HELP, RELOAD, WEB, DEMO, MINESKIN, HOLO, NPC);
    private static final List<String> HOLO_SUBS = List.of(CREATE, EDIT, DELETE, MOVE_HERE, TP, LIST, PATH, ADD_LINE, ADD_ITEM);
    private static final List<String> NPC_SUBS = List.of(CREATE, EDIT, DELETE, MOVE_HERE, TP, LIST, PATH, EQUIP, SKIN);
    private static final Set<String> ID_SUBS = Set.of(EDIT, DELETE, MOVE_HERE, TP, PATH, ADD_LINE, ADD_ITEM, EQUIP, SKIN);
    private static final List<String> PATH_ACTIONS = List.of(ADD, CLEAR);
    private static final String WEB_PORT = "port";
    private static final String WEB_INFO = "info";
    private static final String WEB_RANDOM = "random";
    private static final String WEB_PORT_PATH = "web.port";
    private static final int WEB_PORT_RANDOM_VALUE = 0;
    private static final List<String> WEB_SUBS = List.of(WEB_PORT, WEB_INFO);

    private final Nexora plugin;

    public NexoraCommand(Nexora plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "cmd.player-only");
            return true;
        }
        var sub = args.length == 0 ? null : args[0].toLowerCase(Locale.ROOT);
        if (!allowed(player, sub)) {
            plugin.messages().send(player, "cmd.no-permission");
            return true;
        }
        if (sub == null) {
            plugin.menus().openMain(player);
            return true;
        }
        switch (sub) {
            case RELOAD -> {
                plugin.reloadAll();
                plugin.messages().send(player, "cmd.reloaded");
            }
            case WEB -> web(player, args);
            case MINESKIN -> mineskin(player, args);
            case DEMO -> {
                if (args.length > 1 && CLEAR.equalsIgnoreCase(args[1])) plugin.menus().clearDemo(player);
                else plugin.menus().spawnDemo(player);
            }
            case HOLO -> object(player, NexoraObject.HOLOGRAM, args);
            case NPC -> object(player, NexoraObject.NPC, args);
            default -> help(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !allowed(player, WEB)) return List.of();
        var kind = args.length > 1 ? kindOf(args[0]) : null;
        var sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        List<String> options = switch (args.length) {
            case 1 -> ROOT;
            case 2 -> WEB.equalsIgnoreCase(args[0]) ? webSubs(player) : DEMO.equalsIgnoreCase(args[0]) ? List.of(CLEAR) : kind == null ? List.of() : HOLO.equalsIgnoreCase(args[0]) ? HOLO_SUBS : NPC_SUBS;
            case 3 -> WEB.equalsIgnoreCase(args[0]) ? webValues(player, sub) : kind != null && ID_SUBS.contains(sub) ? ids(kind) : List.of();
            case 4 -> kind == null ? List.of() : fourth(kind, sub);
            default -> List.of();
        };
        var prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private List<String> fourth(String kind, String sub) {
        return switch (sub) {
            case CREATE -> NexoraObject.NPC.equals(kind) ? entityTypes() : List.of();
            case PATH -> PATH_ACTIONS;
            case EQUIP -> plugin.menus().slots().stream().map(EquipmentSlot::name).toList();
            case SKIN -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            default -> List.of();
        };
    }

    private List<String> entityTypes() {
        var prop = plugin.schema().props(NexoraObject.NPC).get(ENTITY_TYPE);
        return prop == null ? List.of() : prop.options().stream().map(o -> o.id().toLowerCase(Locale.ROOT)).toList();
    }

    private List<String> ids(String kind) {
        return plugin.objects().list(kind).stream().map(NexoraObject::id).toList();
    }

    private String kindOf(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case HOLO -> NexoraObject.HOLOGRAM;
            case NPC -> NexoraObject.NPC;
            default -> null;
        };
    }

    private boolean allowed(Player player, String sub) {
        var config = plugin.getConfig();
        return player.hasPermission(config.getString("permissions.admin", ""))
            || WEB.equals(sub) && player.hasPermission(config.getString("permissions.web", ""));
    }

    private void mineskin(Player player, String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            plugin.messages().send(player, "cmd.mineskin-usage");
            return;
        }
        plugin.getConfig().set(MINESKIN_KEY_PATH, args[1].trim());
        plugin.scheduler().runAsync(plugin::saveConfig);
        plugin.objects().restartAll();
        plugin.messages().send(player, "cmd.mineskin-set");
    }

    private void help(Player player) {
        plugin.messages().send(player, "cmd.help");
    }

    private boolean isAdmin(Player player) {
        return player.hasPermission(plugin.getConfig().getString("permissions.admin", ""));
    }

    private List<String> webSubs(Player player) {
        return isAdmin(player) ? WEB_SUBS : List.of();
    }

    private List<String> webValues(Player player, String sub) {
        return isAdmin(player) && WEB_PORT.equals(sub) ? List.of(WEB_RANDOM) : List.of();
    }

    private void web(Player player, String[] args) {
        if (!plugin.getConfig().getBoolean("web.enabled")) {
            plugin.messages().send(player, "cmd.web-disabled");
            return;
        }
        var action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        var manage = WEB_PORT.equals(action) || WEB_INFO.equals(action);
        if (manage && !isAdmin(player)) {
            plugin.messages().send(player, "cmd.no-permission");
            return;
        }
        switch (action) {
            case WEB_PORT -> webPort(player, args.length > 2 ? args[2] : "");
            case WEB_INFO -> webInfo(player);
            default -> plugin.web().sendLink(player);
        }
    }

    private void webPort(Player player, String raw) {
        var random = WEB_RANDOM.equalsIgnoreCase(raw);
        var port = random ? WEB_PORT_RANDOM_VALUE : parseWebPort(raw);
        if (port < 0) {
            plugin.messages().send(player, "web-port-invalid", Map.of("min", String.valueOf(WebServer.MIN_PORT), "max", String.valueOf(WebServer.MAX_PORT)));
            return;
        }
        plugin.getConfig().set(WEB_PORT_PATH, port);
        plugin.scheduler().runAsync(plugin::saveConfig);
        plugin.web().restart(random).whenComplete((bound, error) ->
            plugin.scheduler().runAtEntity(player, () -> webPortResult(player, bound, error)));
    }

    private void webPortResult(Player player, Integer bound, Throwable error) {
        if (error != null || bound == null || bound < 0) {
            plugin.messages().send(player, "web-port-failed");
            return;
        }
        plugin.messages().send(player, "web-port-changed", Map.of("port", String.valueOf(bound), "host", plugin.web().host()));
    }

    private int parseWebPort(String raw) {
        try {
            var value = Integer.parseInt(raw.trim());
            return value >= WebServer.MIN_PORT && value <= WebServer.MAX_PORT ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void webInfo(Player player) {
        var port = plugin.web().port();
        if (port < 0) {
            plugin.messages().send(player, "web-disabled");
            return;
        }
        plugin.messages().send(player, "web-info", Map.of("host", plugin.web().host(), "port", String.valueOf(port)));
    }

    private void object(Player player, String kind, String[] args) {
        if (args.length < 2) {
            help(player);
            return;
        }
        var sub = args[1].toLowerCase(Locale.ROOT);
        if (LIST.equals(sub)) {
            list(player, kind);
            return;
        }
        if (args.length < 3) {
            if (EDIT.equals(sub)) plugin.menus().openList(player, kind);
            else help(player);
            return;
        }
        var id = args[2];
        if (CREATE.equals(sub)) {
            create(player, kind, id, args.length > 3 ? args[3] : null);
            return;
        }
        var found = plugin.objects().get(kind, id);
        if (found.isEmpty()) {
            plugin.messages().send(player, "cmd.not-found", Map.of("id", id));
            return;
        }
        manage(player, found.get(), sub, args);
    }

    private void manage(Player player, NexoraObject obj, String sub, String[] args) {
        Map<String, String> ph = Map.of("id", obj.id());
        switch (sub) {
            case EDIT -> plugin.menus().openEditor(player, obj);
            case DELETE -> {
                plugin.objects().delete(obj);
                plugin.messages().send(player, "cmd.deleted", ph);
            }
            case MOVE_HERE -> {
                var location = player.getLocation();
                mutate(player, obj, o -> o.moveTo(location), "cmd.moved", ph);
            }
            case TP -> teleport(player, obj);
            case PATH -> path(player, obj, args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "");
            case ADD_LINE -> addLine(player, obj, args);
            case ADD_ITEM -> addItem(player, obj);
            case EQUIP -> equip(player, obj, args);
            case SKIN -> skin(player, obj, args);
            default -> help(player);
        }
    }

    private void list(Player player, String kind) {
        var ids = ids(kind);
        Map<String, String> ph = Map.of("kind", plugin.messages().raw(KINDS_KEY + kind + ".plural"), "ids", String.join(", ", ids));
        plugin.messages().send(player, ids.isEmpty() ? "cmd.list-empty" : "cmd.list", ph);
    }

    private void create(Player player, String kind, String id, String entityType) {
        var objects = plugin.objects();
        var kindName = plugin.messages().raw(KINDS_KEY + kind + ".singular");
        if (!objects.validId(id)) {
            plugin.messages().send(player, "cmd.invalid-id", Map.of("id", id));
            return;
        }
        if (objects.exists(kind, id)) {
            plugin.messages().send(player, "cmd.exists", Map.of("kind", kindName, "id", id));
            return;
        }
        var location = player.getLocation();
        NexoraObject created = NexoraObject.HOLOGRAM.equals(kind)
            ? objects.createHologram(id, location)
            : objects.createNpc(id, location, plugin.menus().entityType(entityType));
        plugin.messages().send(player, "cmd.created", Map.of("kind", kindName, "id", id));
        plugin.menus().openEditor(player, created);
    }

    private void teleport(Player player, NexoraObject obj) {
        var anchor = obj.anchor();
        if (anchor.getWorld() == null) {
            plugin.messages().send(player, "cmd.not-found", Map.of("id", obj.id()));
            return;
        }
        player.teleportAsync(anchor);
        plugin.messages().send(player, "cmd.teleported", Map.of("id", obj.id()));
    }

    private void path(Player player, NexoraObject obj, String action) {
        Map<String, String> ph = Map.of("id", obj.id());
        switch (action) {
            case ADD -> {
                if (obj.waypointCount() >= plugin.getConfig().getInt("limits.max-waypoints")) {
                    plugin.messages().send(player, "cmd.path-max", ph);
                    return;
                }
                var location = player.getLocation();
                mutate(player, obj, o -> o.addWaypoint(location), "cmd.path-added", ph);
            }
            case CLEAR -> mutate(player, obj, NexoraObject::clearWaypoints, "cmd.path-cleared", ph);
            default -> help(player);
        }
    }

    private void addLine(Player player, NexoraObject obj, String[] args) {
        if (!(obj instanceof Hologram holo) || args.length < 4) {
            help(player);
            return;
        }
        var text = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).replace(NEWLINE_ESCAPE, NEWLINE);
        appendLine(player, holo, Map.of(PROP_TEXT, text), null);
    }

    private void addItem(Player player, NexoraObject obj) {
        if (!(obj instanceof Hologram holo)) {
            help(player);
            return;
        }
        var item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            plugin.messages().send(player, "cmd.no-item");
            return;
        }
        appendLine(player, holo, Map.of(PROP_TYPE, TYPE_ITEM), ItemCodec.encode(item.asOne()));
    }

    private void appendLine(Player player, Hologram holo, Map<String, String> props, String itemData) {
        Map<String, String> ph = Map.of("id", holo.id(), "max", String.valueOf(plugin.getConfig().getInt("limits.max-lines")));
        plugin.menus().addLine(holo, props, itemData)
            .thenAccept(added -> reply(player, added ? "cmd.line-added" : "cmd.line-max", ph))
            .exceptionally(ex -> missing(player, holo));
    }

    private void equip(Player player, NexoraObject obj, String[] args) {
        if (!(obj instanceof Npc npc) || args.length < 4) {
            help(player);
            return;
        }
        var slot = plugin.menus().slots().stream().filter(s -> s.name().equalsIgnoreCase(args[3])).findFirst();
        if (slot.isEmpty()) {
            plugin.messages().send(player, "cmd.invalid-slot", Map.of("slot", args[3]));
            return;
        }
        var held = player.getInventory().getItemInMainHand();
        Map<String, String> ph = Map.of("id", npc.id(), "slot", slot.get().name());
        plugin.menus().setEquipment(npc, slot.get(), held.isEmpty() ? null : held.asOne())
            .thenRun(() -> reply(player, held.isEmpty() ? "cmd.equip-cleared" : "cmd.equip-set", ph))
            .exceptionally(ex -> missing(player, npc));
    }

    private void skin(Player player, NexoraObject obj, String[] args) {
        if (!(obj instanceof Npc npc) || args.length < 4) {
            help(player);
            return;
        }
        var source = args[3];
        var target = Bukkit.getPlayerExact(source);
        var texture = target == null ? null : target.getPlayerProfile().getProperties().stream()
            .filter(property -> TEXTURES.equals(property.getName())).findFirst().orElse(null);
        Map<String, String> values = new LinkedHashMap<>();
        if (texture != null) {
            values.put(SKIN_NAME, "");
            values.put(SKIN_VALUE, texture.getValue());
            values.put(SKIN_SIGNATURE, texture.getSignature() == null ? "" : texture.getSignature());
        } else {
            values.put(SKIN_NAME, source);
            values.put(SKIN_VALUE, "");
            values.put(SKIN_SIGNATURE, "");
        }
        mutate(player, npc, n -> n.apply(values), "cmd.skin-set", Map.of("id", npc.id(), "skin", source));
    }

    private <T extends NexoraObject> void mutate(Player player, T obj, Consumer<T> change, String key, Map<String, String> ph) {
        plugin.objects().mutate(obj, change)
            .thenRun(() -> reply(player, key, ph))
            .exceptionally(ex -> missing(player, obj));
    }

    private void reply(Player player, String key, Map<String, String> ph) {
        plugin.scheduler().runAtEntity(player, () -> plugin.messages().send(player, key, ph));
    }

    private Void missing(Player player, NexoraObject obj) {
        reply(player, "cmd.not-found", Map.of("id", obj.id()));
        return null;
    }
}
