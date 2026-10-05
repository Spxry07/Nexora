# Nexora — Spec

Packet-only holograms and NPCs for Paper/Folia 1.21.11. No dependencies.

## Platform
- Paper 1.21.11 (Dialog API needs 1.21.7+, Mannequin needs 1.21.9+), Folia supported.
- Maven + `ca.bkaw:paper-nms-maven-plugin` for Mojang-mapped NMS. CI runs `mvn paper-nms:init` then `mvn package`.
- Jar manifest `paperweight-mappings-namespace: mojang`.

## Rendering model
- Every visible thing is a *template* entity made with `World#createEntity` (never added to the world), configured through the normal Paper API, then turned into packets via NMS (`ClientboundAddEntityPacket`, `SynchedEntityData#getNonDefaultValues/packDirty`).
- Holograms: one Display entity (Text/Item/Block) per line.
- NPCs: any living entity type; `MANNEQUIN` gives player skins (name or texture value/signature).
- Clicks: Paper `PlayerUseUnknownEntityEvent` (no netty injection).

## Threading
- Each object owns a repeating region task at its anchor (`RegionScheduler`). Templates, animations and edits run there.
- Each player owns an entity-scheduler timer (Tracker): range checks, spawn/despawn, NPC look-at (per viewer).
- Spawn/despawn and template swaps synchronize on the object so a viewer never keeps a ghost entity.
- YAML I/O on single thread `nexora-io`; web server on single thread `nexora-web`.

## Features
- Hologram motion: spin, bob, orbit, pulse, sway, waypoint path; particles.
- Line effects: frames (`||`), typewriter, rainbow, wave gradient, scroll, blink; placeholders `{online} {max} {time} {date}`.
- NPC: skin, name + description, pose, glow + color (team packet), scale, equipment, look-at, swing/sneak/hurt loops, spin, path walking, click actions (`[console] [player] [message] [actionbar] [sound]`), cooldown, click type.

## Editing
- `editor.yml` defines every property (type, range, options, label) and the sections. Java only binds ids to fields.
- Same schema drives Dialog screens (in-game) and the web editor; every value is normalized and clamped server-side.
- Web editor: JDK `HttpServer`, single page `web/index.html`, bearer token from `/nexora web` (token in URL fragment, expires).

## Commands
`/nexora` (alias `/nx`) — main dialog. Subcommands: `help`, `reload`, `web`,
`holo|npc create|edit|delete|movehere|tp|list <id>`, `holo addline <id> <text>`, `holo additem <id>`,
`holo|npc path <id> add|clear`, `npc equip <id> <slot>`, `npc skin <id> <player|name>`.
