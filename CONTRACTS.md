# Nexora — Agent Contracts

Read `SPEC.md` first. Package root `net.spxry.nexora`, sources in `src/main/java/net/spxry/nexora/`.
Target Paper **1.21.11** (Dialog API + Mannequin). NMS is Mojang-mapped (paper-nms), only `nms/Packets.java` touches NMS types except `net.minecraft.network.protocol.Packet<?>` which object classes may use as an opaque type.

## Hard rules (every agent)
- NEVER run anything locally: no mvn, no gradle, no java, no servers. Write files only. CI compiles.
- Zero comments in Java (`//`, `/* */`, javadoc). No wildcard imports. Java 21 (`var`, records, switch expressions, pattern matching).
- Zero hardcoded user-facing strings/numbers/permissions/sounds: read from `config.yml`, `messages.yml`, `editor.yml` (already written, read them).
- No `Bukkit.getScheduler()`; use `plugin.scheduler()` (`scheduler/FoliaScheduler.java`). Player work → `runAtEntity`, location work → `runAtLocation`, console commands → `runGlobal`.
- `ConcurrentHashMap` for shared state. No `System.out`, no `printStackTrace` (`plugin.getLogger().log(Level.WARNING, msg, e)`).
- No deprecated Paper API (e.g. use `customName(Component)`, adventure `Component`s, `MessageManager.play` for sounds; sound names are Minecraft keys like `entity.villager.no`).
- Only edit files you own (below). If you need something from a core file, say so in your final report instead of editing it.

## File ownership
| Agent | Files |
|---|---|
| core (done) | `Nexora.java`, `scheduler/FoliaScheduler`, `util/ColorUtil`, `util/ItemCodec`, `config/MessageManager`, `nms/Packets`, `edit/Schema`, `edit/Binding`, `object/NexoraObject`, `object/ObjectManager`, `object/Tracker`, `storage/ObjectStore`, all YAML in resources |
| holograms | `object/Hologram.java`, `object/HoloLine.java`, `object/TextEffects.java` |
| npcs | `object/Npc.java`, `npc/ActionRunner.java`, `listener/InteractListener.java` |
| dialogs | `dialog/DialogMenus.java`, `command/NexoraCommand.java` |
| web | `web/WebServer.java`, `src/main/resources/web/index.html` |

messages.yml keys: dialogs and web agents write the YAML they need into a fragment file (path given in your prompt); core merges it into `messages.yml`. Use the message schema from existing `messages.yml` for chat messages (`overall-enabled`, `messages-enabled`, `messages`, `actionbar-enabled`, `Actionbar`, `sound-enabled`, `sound`, `sound-volume`, `sound-pitch`); plain string keys for labels/titles. Accent `&#6293F0`, denial lines fully `&#FF0000`, small caps for titles/button labels, prefix placeholder `{prefix}`.

## Core API you can call

### `Nexora` (main)
`static Nexora get()`, `FoliaScheduler scheduler()`, `MessageManager messages()`, `Schema schema()`, `ObjectManager objects()`, `Tracker tracker()`, `DialogMenus menus()`, `WebServer web()`, `ActionRunner actions()`, `void reloadAll()`; `getConfig()`.

### `MessageManager`
`send(CommandSender, key)`, `send(CommandSender, key, Map<String,String> ph)` (placeholders written `{name}` in yaml, keys without braces in map), `Component component(key)`, `component(key, ph)`, `boolean has(key)`, `String raw(key)`, `raw(key, ph)`, `List<String> list(key)`, `static play(Audience, String soundKey, float vol, float pitch)`, `static String apply(String, Map)`.

### `ColorUtil`
`Component colorize(String legacyAmpersandWithHex)`, `String plain(String legacy)` (visible text only), `String stripColor(String)`.

### `ItemCodec`
`String encode(ItemStack)` (null for empty), `Optional<ItemStack> decode(String)`.

### `Schema` (editor.yml)
Kinds: `"hologram"`, `"line"`, `"npc"` (constants `NexoraObject.HOLOGRAM/LINE/NPC`).
`enum Type {TEXT, MULTILINE, NUMBER, INTEGER, BOOL, CHOICE, COLOR}`; `record Prop(id, label, type, min, max, step, List<Option> options)`; `record Option(id, label)`; `record Section(id, title, List<Prop> props)`.
`Map<String, Prop> props(kind)`, `List<Section> sections(kind)`, `Optional<Section> section(kind, id)`,
`<T> Map<String,String> read(Map<String,Binding<T>>, T)`, `<T> List<String> apply(kind, Map<String,Binding<T>>, T, Map<String,String> raw)` → returns invalid ids, normalizes/clamps every value.
`static String normalize(Prop, String)`, `static double clamp(Prop, double)`, `static String format(double)`.
TEXT `max` = max length. COLOR values are `#RRGGBB` or `#AARRGGBB`.

### `Binding<T>` (record getter/setter over strings)
`Binding.text(getter, setter)`, `Binding.number(ToDoubleFunction, ObjDoubleConsumer)`, `Binding.integer(ToIntFunction, ObjIntConsumer)`, `Binding.bool(Predicate, BiConsumer<T,Boolean>)`.

### `Packets` (NMS; return `Packet<?>`, may return null = nothing to send)
`Entity handle(org.bukkit.entity.Entity)` (net.minecraft Entity), `spawn(handle, x, y, z, yaw, pitch)`, `fullData(handle)` (all non-default metadata, also clears dirty), `dirtyData(handle, boolean restartInterpolation)` (changed metadata; true re-triggers Display transformation interpolation), `teleport(id, x, y, z, yaw, pitch)`, `rotation(id, yaw, pitch)`, `head(handle, yaw)`, `swing(handle, offHand)`, `hurt(id, yaw)`, `equipment(id, Map<EquipmentSlot, ItemStack>)`, `attributes(handle)`, `teamCreate(teamName, entry, namedColor)`, `teamRemove(teamName)`, `destroy(int... ids)`, `bundle(List<Packet<?>>)`, `send(Player, Packet<?>)` (thread-safe, null-safe).

### `NexoraObject` (base of Hologram and Npc, package `object`)
Package-private fields usable by subclasses: `waypoints` (List<Vector>), `viewRange`, `pathSpeed`, `particle`, `particleCount`, `particleRadius`, `particleInterval`. Protected: `plugin`, `long ticks`.
Abstract to implement: `String kind()`, `Map<String,String> values()` (= `plugin.schema().read(BINDINGS, this)`), `List<String> apply(Map<String,String>)` (= `plugin.schema().apply(kind(), BINDINGS, this, values)`), `Built build(Location anchor)`, `void tick(int interval)`.
Optional hooks: `writeExtra(Map<String,Object> out)`, `readExtra(ConfigurationSection section)`, `onHide(UUID viewer)`.
`record Built(List<Packet<?>> spawn, List<Packet<?>> despawn, int[] ids)`.
Helpers: `static <T extends NexoraObject> Map<String,Binding<T>> commonBindings()` (view-range, path-speed, particle, particle-count, particle-radius, particle-interval — start your BINDINGS map from it), `refreshSpawn(List<Packet<?>>)` (replace the spawn bundle new viewers get; call when position/metadata changed, BEFORE broadcasting), `broadcast(List<Packet<?>>)`, `broadcast(List<Packet<?>>, Set<UUID> except)`, `viewers()`, `setPosition(x,y,z)` (current visual position for tracker), `double[] pathPoint(double seconds)` → `{x,y,z,yaw}` or null, `particles(cx, cy, cz, interval)`.
Public: `id()`, `anchor()` (clone), `x() y() z()`, `viewRange()`, `isRemoved()`, `entityIds()`, `waypointCount()`, `moveTo(Location)`, `addWaypoint(Location)`, `clearWaypoints()`, `inRange(Location)`, `isViewer(UUID)`, `show(Player)`, `hide(Player)`, `forget(UUID)`, `start()`, `restart()`, `remove()`, `snapshot()`, `restore(ConfigurationSection)`.
Threading: `build` and `tick` run on the anchor's region thread. Every field mutation from outside goes through `ObjectManager.mutate` (same region thread), which then calls `restart()` (rebuild + respawn for viewers) and saves.

Rendering technique: create template entities with `anchor.getWorld().createEntity(location, SomeEntity.class)` (NOT spawned), configure with normal Paper API, take `Packets.handle(entity)`; ids = `handle.getId()`. Spawn list = `spawn(...)` + `fullData(handle)` (+ extras). Updates in `tick`: mutate the template through Paper API, then `dirtyData(handle, ...)`; movement via `teleport(...)`.

### `ObjectManager`
`Collection<NexoraObject> all()`, `List<NexoraObject> list(String kind)` (sorted by id), `Optional<NexoraObject> get(kind, id)`, `Optional<NexoraObject> byEntityId(int)`, `boolean validId(String)`, `boolean exists(kind, id)`,
`Hologram createHologram(String id, Location)`, `Npc createNpc(String id, Location, String entityType)` (caller checks `validId`/`exists` first),
`HoloLine newLine()` (defaults from `config.yml defaults.line`), `Map<String,String> defaults(String kind)`,
`<T extends NexoraObject> CompletableFuture<Void> mutate(T obj, Consumer<T> change)` (runs on obj region thread, then restart + save; completes exceptionally if removed),
`<T extends NexoraObject, R> CompletableFuture<R> query(T obj, Function<T,R> fn)` (read state on region thread),
`void delete(NexoraObject)`.
Futures complete on a region thread: hop back with `plugin.scheduler().runAtEntity(player, ...)` before touching a player; always add `.exceptionally(...)`.

### `Tracker`
Per-player visibility; you never need to call it.

## Agent-provided API (must match exactly; core calls these)

### holograms
`public final class Hologram extends NexoraObject`:
`public Hologram(Nexora plugin, String id, Location anchor)`, `public static final Map<String, Binding<Hologram>> BINDINGS` (ids = editor.yml `kinds.hologram.properties`), `public List<HoloLine> lines()` (live `CopyOnWriteArrayList`, only mutate inside `mutate`), `kind()` returns `HOLOGRAM`.
Snapshot extras: `lines` = list of maps `{props: Map<String,String> (line values), item: base64 or absent}`.
`public final class HoloLine`: `public HoloLine()`, `public static final Map<String, Binding<HoloLine>> BINDINGS` (ids = `kinds.line.properties`), `public Map<String,String> values(Schema)`, `public List<String> apply(Schema, Map<String,String>)`, `public HoloLine copy()`, `public String type()`, `public String text()`, `public String itemData()`, `public void itemData(String base64OrNull)`.
`TextEffects` — static rendering of line text per tick (effects + frames + placeholders).

### npcs
`public final class Npc extends NexoraObject`: `public Npc(Nexora plugin, String id, Location anchor)`, `public static final Map<String, Binding<Npc>> BINDINGS` (ids = `kinds.npc.properties`), `public Map<EquipmentSlot, ItemStack> equipment()` (live ConcurrentHashMap, mutate inside `mutate`), `public void click(Player player, boolean attack)` (called on the player's thread), `public void look(Player viewer, Location viewerEye)` (called by Tracker on the viewer's thread each tracker tick while in range), `kind()` returns `NPC`.
Snapshot extras: `equipment` = map slot name → base64.
`public final class ActionRunner`: `public ActionRunner(Nexora plugin)`, `public void run(Player player, String actions, Map<String,String> placeholders)`.
`public final class InteractListener implements Listener`: `public InteractListener(Nexora plugin)`.

### dialogs
`public final class DialogMenus`: `public DialogMenus(Nexora plugin)`, `openMain(Player)`, `openList(Player, String kind)`, `openEditor(Player, NexoraObject)`, `openCreate(Player, String kind)`.
`public final class NexoraCommand implements CommandExecutor, TabCompleter`: `public NexoraCommand(Nexora plugin)`.

### web
`public final class WebServer`: `public WebServer(Nexora plugin)`, `public void start()`, `public void stop()`, `public void sendLink(Player)`.
