# NPC Triggers + Nameplate Holograms (round 6)

## Props (lead adds to editor.yml + config defaults)
npc: `triggers` (multiline, max 8192), `look-trigger-range` (1-32), `approach-range` (1-32), `nameplate` (bool), `nameplate-height` (-1..5, above the head).
hologram: `attach-to` (text, npc id, empty = none), `attach-height` (-2..6).
config: `npc.triggers.look-angle` (degrees, default 10), `npc.triggers.default-cooldown` (ticks, 40), `npc.triggers.max-rules` (64), `npc.triggers.max-actions` (16), `npc.nameplate.id-suffix` ("-tag").

## Trigger DSL (`triggers` prop, one rule per line, `#` starts a comment line, blank lines ignored)
`<event> [args] [every <ticks>] [cooldown <ticks>] -> <action>; <action>; ...`

Events (player-scoped events have a {player}; npc-scoped run for all viewers):
| event | scope | fires |
|---|---|---|
| `click` / `rightclick` / `leftclick` | player | NPC clicked (existing click path; runs in addition to `actions`) |
| `look` | player | player starts looking at the NPC (eye ray within look-angle of the NPC eye/body centre, distance ≤ look-trigger-range); re-arms after looking away |
| `lookaway` | player | player stops looking |
| `approach` | player | player moves inside approach-range |
| `leave` | player | player moves back outside approach-range |
| `holding <material>` | npc | while the NPC's current main-hand item (equipment or gesture override) matches; needs `every N` (default 20) |
| `frame <n>` | npc | animation frame n (1-based) starts |
| `swing` | npc | any swing (interval or gesture swing) |
| `use` / `usestop` | npc | gesture use flag turns on / off |
| `interval <ticks>` | npc | every N ticks |
| `spawn` | player | NPC becomes visible to a player |
`cooldown` is per player for player-scoped events, per NPC otherwise; default `npc.triggers.default-cooldown` for player events, 0 for npc events.

Actions (`;`-separated, placeholders `{player}` `{npc}` `{uuid}` for player-scoped events):
- `particle <TYPE> [count] [anchor] [spread] [speed]` — TYPE is any org.bukkit.Particle with no data, or `DUST:<#RRGGBB>[:size]`, `DUST_TRANSITION:<#from>:<#to>[:size]`, `ITEM:<material>`, `BLOCK:<material>`. anchor: `head` (eye), `hand` (main-hand side), `offhand`, `feet`, `body`, `above` (above the nameplate), `around` (random ring around the body, radius = spread). Sent per viewer with Player#spawnParticle (player-scoped → only that player unless suffixed `all`: `particle! ...` = everyone in range).
- `sound <key> [volume] [pitch]` (`sound!` = all viewers, at the NPC location)
- `message <text>` · `actionbar <text>` · `title <title>|<subtitle>` (player events only; npc events send to all viewers)
- `console <command>` · `player <command>` (player events only)
- `swing main|off` · `glow on|off|toggle` · `animate on|off|restart` · `item <material|none>` (temporary main-hand override until next `item`) · `look` (snap head to that player) · `hurt`
- Malformed rules/actions are skipped (never throw); limits from config.

## Nameplate
- `nameplate: true` → ObjectManager ensures a hologram `<npcId><suffix>` exists with `attach-to: <npcId>` (created with lines from the NPC name + description on first enable), the NPC's own custom name/description are then hidden. `nameplate: false` keeps the hologram but detaches nothing; deleting an NPC deletes its nameplate hologram.
- Hologram with `attach-to`: each tick its centre = attached NPC current position (`npc.x()/y()/z()`) + eye height×scale + `attach-height`; it moves with walking NPCs (teleport packets, interpolated). If the NPC doesn't exist, fall back to its own anchor.
