# Nexora Web API v2 (contract between WebServer.java and web/index.html)

All `/api/*` calls: header `Authorization: Bearer <token>`, JSON bodies, errors `{error: "text"}` with HTTP status. Existing endpoints stay unchanged unless listed here.

## Assets (config.yml `web.assets.*`, exposed in `GET /api/schema` → `config.assets`)
```json
"assets": {
  "texture": "https://assets.mcasset.cloud/1.21.11/assets/minecraft/textures/{type}/{name}.png",
  "skin":    "https://mc-heads.net/skin/{id}",
  "head":    "https://mc-heads.net/avatar/{id}/{size}",
  "body":    "https://mc-heads.net/body/{id}/{size}",
  "viewer":  "https://cdn.jsdelivr.net/npm/skinview3d@3.1.0/bundles/skinview3d.bundle.js",
  "font":    "https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@400;600&display=swap"
}
```
`{type}` is `item` or `block`; `{name}` lowercase material id. `{id}` = NPC skin id (texture hash or player name; empty → use "MHF_Steve").
The page CSP must allow exactly these hosts: img-src data: + texture/skin/head/body hosts; script-src 'unsafe-inline' + viewer host; style-src 'unsafe-inline' + font css host; font-src fonts.gstatic.com (config `web.assets.font-files-host`); connect-src 'self' + skin host. WebServer builds the CSP from the configured URLs.

## New / changed endpoints
- `GET /api/me` → `{name, uuid, online, world, x, y, z, yaw}` (session player; position read on the player's entity thread; `online:false` and no position when offline).
- `GET /api/materials` → `{items: ["diamond_sword", ...], blocks: ["stone", ...]}` lowercase, non-legacy, items = `isItem()`, blocks = `isBlock() && isItem()`; cached after first call.
- `POST /api/terrain {world, x, z, radius}` → `{x0, z0, size, colors: [int rgb or -1], heights: [int or -32768]}` row-major (`index = dz*size + dx`), `size = 2*radius+1`, radius clamped to `web.terrain-max-radius` (default 48). Top block via `world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE)`, color `block.getBlockData().getMapColor().asRGB()`. Runs on `runAtLocation(center)`; a column is read only if its chunk is loaded and `Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)`, else -1.
- `POST /api/move {kind, id, x, y, z, yaw}` → move anchor to exact coords in the object's current world (`y`/`yaw` optional = keep). Max distance from current anchor `web.max-move-distance` (default 64). Uses `objects.mutate(o -> o.moveTo(loc))`.
- `POST /api/waypoints {kind, id, points: [[x,y,z], ...]}` → `objects.mutate(o -> o.setWaypoints(list))`; capped by `limits.max-waypoints`; returns `{ok, waypoints: n}`.
- `GET /api/objects` items gain: `yaw`, `entityType` (npc), `skinId` (npc, may be ""), `lines` (hologram line count), `pathPoints`.
- `POST /api/object` gains: `yaw`, `waypointList: [[x,y,z], ...]`, npc `skinId`, `entityType`.

## Core methods available to WebServer
`NexoraObject.waypoints()` (List<Vector> copy), `setWaypoints(List<Vector>)`, `moveTo(Location)`, `anchor()`; `Npc.skinId()`, `Npc.entityType()`; `Hologram.lines()`.
