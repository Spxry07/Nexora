# Round 9 — clickable holograms, shared triggers + signals, shapes, reactive motion, admin GUI

Props are already in `editor.yml` (file-version 7) and `config.yml` defaults (hologram: shape-*, grid-columns, arc-angle, clickable, hitbox-*, actions, click-type, cooldown, triggers, approach-range, look-trigger-range, face-player, follow-*, proximity-*; line: scale-x/y/z; config `admin.sneak-click-edit`, `admin.default-menu`). The lead owns editor.yml / config.yml / messages.yml / demos.yml — agents list any keys they need in their report.

## 1. Shared trigger engine (agent E owns `npc/Triggers.java`, `npc/Effects.java`, new `npc/TriggerRuntime.java`, new `npc/SignalBus.java`, `object/Npc.java`, `object/Tracker.java`)
`net.spxry.nexora.npc.TriggerHost` (written by lead, do not change) is implemented by Npc and Hologram:
```
String kind(); String id(); double x(); double y(); double z(); float facingYaw();
Set<UUID> viewers(); Location anchor();
Vector anchorPoint(String anchor);            // world position for an anchor name or null if unknown
boolean hostAction(String verb, List<String> args, Player player); // object-specific verbs, true if handled
```
`TriggerRuntime` (agent E, one per object, built with `new TriggerRuntime(plugin, host)`):
- `void rules(String text)` parse once per build (also accepts the legacy NPC grammar).
- `void click(Player p, boolean attack)` player thread; `void observe(Player p, Location eye, Vector dir, double lookRange, double approachRange)` player thread (look/lookaway/approach/leave); `void shown(Player p)` / `void departed(Player p)` / `void forget(UUID)`.
- `void fire(String event, Player p, int value)` for host events (frame/swing/use/usestop/holding...), `void tick(int interval, Predicate<Material> holding)` region thread (interval/holding).
- `void signal(String name, Player source)` — run rules `signal <name>`.
Signals: `plugin.signals()` → `SignalBus` with `emit(String name, Player source)`; delivers to every object (Npc and Hologram expose `runtime()`), scheduled on each object's anchor region via `plugin.scheduler().runAtLocation`. Signal names `[a-z0-9_.-]{1,48}`; loop guard: a signal emitted while handling the same signal (same tick) is dropped; config `triggers.max-signal-depth` (default 4).
Grammar additions (case-insensitive, malformed parts skipped, never throw):
- event `signal <name>`; actions `signal <name>` and `delay <ticks>` (remaining actions of that rule run after the delay, on the same region thread).
- particle placement: `particle[!] <TYPE> [count] [anchor] [spread] [speed] [at <target>] [to <target>] [offset <dx,dy,dz>]` — `<target>` = an anchor name of this object (`head hand offhand feet body above center top bottom line:<n>`), `player`, `npc:<id>[:<anchor>]`, `holo:<id>[:<anchor>]`, `~dx,~dy,~dz` (relative to this object), or `x,y,z` absolute (same world). `to <target>` draws a beam: `count` particles evenly spaced from start to end.
- generic actions: particle, sound, message, actionbar, title, console, player, signal, delay; all other verbs go to `host.hostAction(verb, args, player)`.
- Npc host verbs: swing, glow, animate, item, look, hurt, `animation <PRESET>`.
- Hologram host verbs (agent A): `text <line#> <text>`, `layout <LAYOUT>`, `spin <deg/s> [ticks]`, `pulse [amount] [ticks]`, `scale <factor> [ticks]`, `move <dx,dy,dz> [ticks]`, `glow on|off|toggle`, `hide [ticks]` / `show`, `effect <line#> <EFFECT>` — all runtime-only (reset on rebuild), smooth via display interpolation.

## 2. Holograms (agent A owns `object/Hologram.java`, `object/HoloLine.java`, `listener/InteractListener.java`)
- Implements TriggerHost + embeds TriggerRuntime (rules from `triggers`; `actions` click list runs via ActionRunner like NPCs, respecting click-type + cooldown).
- `clickable` → one Interaction entity template (width/height hitbox-*, centred on the hologram centre + hitbox-offset-y, follows motion/path/follow), its id in Built ids so `PlayerUseUnknownEntityEvent` resolves to the hologram; InteractListener routes Npc and Hologram clicks to `click(...)`.
- Admin sneak-click: in InteractListener, if `admin.sneak-click-edit` and the player is sneaking and has `permissions.admin` → `plugin.guis().openEditor(player, object)` instead of running actions.
- Layout positions from `Shapes.place(...)` (agent B) instead of the inline switch; keep wave/bob/orbit/path/offsets.
- Line `scale-x/y/z` multiply the line scale per axis (transformation scale vector).
- Reactive motion (region thread, viewer positions from a ConcurrentHashMap of last eye locations fed by `observe`): `face-player` smoothly turns the hologram yaw toward the nearest viewer within view range; `follow-player` glides the centre toward the nearest viewer within follow-range, stopping at follow-distance, at follow-speed blocks/s (visual offset only, anchor unchanged); `proximity-scale` adds up to +N× scale as the nearest viewer comes within proximity-range.
- Tracker must call the hologram's observe too (agent E edits Tracker to call `runtime().observe` for any TriggerHost via `object instanceof TriggerHost`). Agent A exposes `public TriggerRuntime runtime()`, `lookRange()`, `approachRange()`.

## 3. Shapes (agent B owns new `object/Shapes.java`)
`public static double[] place(String layout, Shapes.Params p, int index, int count, double turnRad)` → `{x, y, z, yawDeg}` LOCAL offset (x = right, y = up, z = forward of the hologram) + item facing yaw relative to the hologram.
`public record Params(double radius, double width, double height, double tilt, int sides, double inner, int columns, double arc, double spacing, double helixStep, double[] stackOffsets)`.
Layouts: STACK (uses stackOffsets[index]), ROW, RING, WHEEL, HELIX, TORNADO, SPHERE (existing behaviour), TRIANGLE, SQUARE, POLYGON (sides), STAR (2×sides vertices alternating radius and radius·inner), HEART, GRID (columns, spacing), ARC (arc degrees), SPIRAL (flat Archimedean), CUBE (12 edges, points evenly along total edge length), INFINITY (lemniscate), DIAMOND (vertical rhombus), PYRAMID (square base + apex edges), CROSS (plus sign). Closed outlines distribute points evenly along the perimeter. width/height stretch the shape's local X / Y-or-Z axes; tilt rotates flat shapes from horizontal (0) to vertical (90) around the local X axis. turnRad rotates the whole shape around its vertical axis. Pure math, no Bukkit imports.

## 4. Admin GUI (agent C owns new package `gui/` + `src/main/resources/gui/*.yml`)
`net.spxry.nexora.gui.GuiManager` (stub written by lead: `openMain(Player)`, `openEditor(Player, NexoraObject)`), plus all GUIs. Rules from `~/.claude/CLAUDE.md` + minecraft skill §3/§6: own InventoryHolder per GUI, `instanceof` detection, small-caps titles/names, filler GRAY_STAINED_GLASS_PANE " ", ≤1 glow, lore header `&8Description:` + CTA, accent #6293F0, sounds from config.
Screens: Main (holograms, npcs, create, demo, web link, reload), paged object list (head icons for NPCs, sorted, search via dialog text input), Hologram editor, NPC editor, Lines list + line editor, Equipment (real drag-and-drop slots), Animation preset picker, Shape picker (all layouts with icons), Effect picker, Triggers quick menu (add starter rules, open web), Confirm delete. Value editing via schema props: BOOL toggle, CHOICE cycle (left next / right previous), NUMBER/INTEGER ± step (shift ×10), TEXT/MULTILINE/COLOR → Paper Dialog single input (reuse DialogMenus helpers or own minimal one). Edits through `plugin.objects().mutate`. `/nexora` opens the GUI when `admin.default-menu` is GUI (dialogs stay reachable via a button and `/nexora dialog`). Command routing: agent C may edit `command/NexoraCommand.java` (only to add `gui`/`dialog` subcommands + default menu switch).

## 5. Web (agent D owns `src/main/resources/web/index.html`, `web/WebServer.java`)
Hologram Interaction tab (clickable, hitbox with 3D box in preview, actions, triggers builder reused from NPCs incl. the new signal/at/to/beam grammar and hologram verbs), Reactive tab, Shape gallery with every new layout (animated mini previews matching Shapes math) + size controls, line scale X/Y/Z, signal "links" view: list of signals emitted/listened per object with jump links. WebServer: `/api/objects` items gain `signals: {emits:[], listens:[]}` parsed cheaply from triggers text (regex on the raw prop strings).
