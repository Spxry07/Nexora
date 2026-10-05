# Nexora

Packet-based animated holograms and NPCs for Paper / Folia **1.21.11**, with an in-game Dialog editor and a web editor served straight from the plugin jar. No dependencies.

## Install
1. Drop `Nexora-1.0.jar` into `plugins/` on a Paper or Folia 1.21.11 server and start it.
2. Open TCP port **8155** on your host/firewall (the web editor). Change it with `web.port` in `config.yml`.
3. In game: `/nexora` opens the menu. `/nexora web` gives you a private link to the web editor.

The link uses the same address you joined the server with (e.g. `play.example.com:8155`). Set `web.public-url` if the editor sits behind a reverse proxy or a different domain.

## Quick start
- `/nexora demo` – spawns an animated NPC (your skin), a walking NPC and two animated holograms in front of you. `/nexora demo clear` removes them.
- `/nexora npc skin guard Notch` – copy a skin (online player = exact textures, otherwise by name).
- `/nexora npc equip guard hand` – put your held item in the NPC's hand.
- `/nexora npc path guard add` (repeat at each point) + set *walk speed* – patrol route.


## Permissions
- `nexora.admin` – everything (default: op)
- `nexora.web` – web editor link (default: op)

## Security
The web editor is plain HTTP on `0.0.0.0:8155`. Links carry a random token that expires after `web.session-minutes`. For public servers put it behind HTTPS (nginx/Caddy) and set `web.public-url`, or bind to `127.0.0.1` and tunnel.

## Build
CI (`.github/workflows/build.yml`) runs `mvn paper-nms:init` (Mojang-mapped Paper) then `mvn package`.
