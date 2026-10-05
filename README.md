# Nexora

Packet-based animated holograms and NPCs for Paper / Folia **1.21.11**, with an in-game Dialog editor and a web editor served straight from the plugin jar. No dependencies.

## Install
1. Drop `Nexora-1.0.jar` into `plugins/` on a Paper or Folia 1.21.11 server and start it.
2. The web editor picks a random port (saved in `web-port.txt`); open it on your firewall, or pin one with `/nexora web port <number>`. `/nexora web info` shows the address.
3. In game: `/nexora` opens the menu. `/nexora web` gives you a private link to the web editor.

The link uses the backend server IP (server-ip, then auto-detected public IP). Override with `web.host` or `web.public-url`.

## Quick start
- `/nexora demo` – spawns the showcase: wizard with levitating blocks, solar system, globe, ferris wheel, tornado, text-effects board, your clone, a patrol and a titan. `/nexora demo clear` removes it.
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
