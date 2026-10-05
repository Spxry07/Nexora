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

## Skinned Model NPCs (posable skinned limbs, no resource pack)
1. Get a free API key at https://account.mineskin.org/keys
2. In game: `/nexora mineskin <key>`
3. Set an NPC's type to **Skinned Model (posable)** (web editor → Pose tab → "Switch to Skinned Model").
The NPC's skin is cut into 10 part textures and uploaded once through MineSkin (cached in `skin-parts.yml`; takes a minute or two per new skin). Until then it shows as a normal skinned NPC. Pose every limb in the web Pose tab or use the animation presets (Walk, Dance, Salute, Dab, ...).
If parts look misaligned or face backwards, tune `model.cube-size`, `model.cube-offset-y` and `model.part-yaw` (0 or 180) in `config.yml`, then `/nexora reload`.
