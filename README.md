# Nexora

Packet-based animated holograms and NPCs for Paper / Folia **1.21.11**, with an in-game Dialog editor and a web editor served straight from the plugin jar. No dependencies.

## Install
1. Download the latest `Nexora-<version>.jar` from [Releases](https://github.com/Spxry07/Nexora/releases), drop it into `plugins/` on a Paper or Folia 1.21.11 server and start it.
2. The web editor picks a random port (saved in `web-port.txt`); open it on your firewall, or pin one with `/nexora web port <number>`. `/nexora web info` shows the address.
3. In game: `/nexora` opens the menu. `/nexora web` gives you a private link to the web editor.

The link uses the backend server IP (server-ip, then auto-detected public IP). Override with `web.host` or `web.public-url`.

## Quick start
- `/nexora demo` – spawns the showcase: wizard with levitating blocks, solar system, globe, ferris wheel, tornado, text-effects board, your clone, a patrol and a titan. `/nexora demo clear` removes it.
- `/nexora npc skin guard Notch` – copy a skin (online player = exact textures, otherwise by name).
- `/nexora npc equip guard hand` – put your held item in the NPC's hand.
- `/nexora npc path guard add` (repeat at each point) + set *walk speed* – patrol route.
- `/nexora gui` – admin GUI for everything. Sneak + right-click any NPC or hologram to open its editor directly.


## Permissions
- `nexora.admin` – everything (default: op)
- `nexora.web` – web editor link (default: op)

## Security
The web editor is plain HTTP on `0.0.0.0:8155`. Links carry a random token that expires after `web.session-minutes`. For public servers put it behind HTTPS (nginx/Caddy) and set `web.public-url`, or bind to `127.0.0.1` and tunnel.

## Build
CI (`.github/workflows/build.yml`) runs `mvn paper-nms:init` (Mojang-mapped Paper) then `mvn package` on every push. Publishing a GitHub release `v<version>` runs `.github/workflows/release.yml`, which builds that tag and attaches the jar. The version lives only in `pom.xml`; `plugin.yml` reads it at build time.

## Skinned Model NPCs (posable skinned limbs, no resource pack)
1. Get a free API key at https://account.mineskin.org/keys
2. In game: `/nexora mineskin <key>`
3. Set an NPC's type to **Skinned Model (posable)** (web editor → Pose tab → "Switch to Skinned Model").
The NPC's skin is cut into 10 part textures and uploaded once through MineSkin (cached in `skin-parts.yml`; takes a minute or two per new skin). Until then it shows as a normal skinned NPC. Pose every limb in the web Pose tab or use the animation presets (Walk, Dance, Salute, Dab, ...).
The 180° item-display flip is handled automatically. If parts still look off in your client, `model.part-yaw` adds an extra turn (default 0), and `model.cube-size` / `model.cube-offset-y` adjust size and height; apply with `/nexora reload`.

## License
Copyright (C) 2026 Spxry Studios

Nexora is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY; see [LICENSE](LICENSE) for the full text.

Minecraft is a trademark of Mojang Synergies AB. Nexora is not affiliated with Mojang or Microsoft.

## Triggers, signals and linking
NPCs and holograms share one rule language (`triggers` setting, or the visual builder in the web editor):
```
click -> signal gate.open; particle! TOTEM_OF_UNDYING 20 center 0.5
signal gate.open -> layout STAR; spin 720 40; delay 60; layout RING
signal gate.open -> animation CAST; particle! DUST:#40CEFC:1.2 40 hand 0 0 to holo:orb:center
approach -> message &aHey {player}!
```
- Events: `click`, `rightclick`, `leftclick`, `look`, `lookaway`, `approach`, `leave`, `spawn`, `interval <ticks>`, `holding <item>`, `frame <n>`, `swing`, `use`, `usestop`, `signal <name>`.
- Actions: `particle` (at anchors, `at`/`to` targets incl. `npc:<id>`, `holo:<id>`, `player`, `~x,~y,~z`, beams), `sound`, `message`, `actionbar`, `title`, `console`, `player`, `signal`, `delay`, plus NPC verbs (`swing`, `glow`, `animate`, `animation <preset>`, `item`, `look`, `hurt`) and hologram verbs (`text`, `layout`, `spin`, `pulse`, `scale`, `move`, `glow`, `hide`, `show`, `effect`).
- Holograms become clickable with `clickable: true` (hitbox size/offset configurable) and can face, follow or grow toward nearby players.
- Hologram shapes: stack, ring, helix, row, wheel, sphere, tornado, triangle, square, polygon, star, heart, grid, arc, spiral, cube, infinity, diamond, pyramid, cross — with size, width, height, tilt and per-line X/Y/Z scale.
