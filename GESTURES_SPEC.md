# Skinned Gestures + Skin-Tinted Poseables (round 5)

Vanilla clients cannot render arbitrary limb angles on a skinned player model without a resource pack. We get as close as possible with NMS packets:

## A. Gesture tokens (extend the `animation` keyframe text from ANIMATION_SPEC.md)
Same line format: `<ticks> token token ...`. New tokens (all optional, case-insensitive values):
- `pose=STANDING|SNEAKING|SWIMMING|FALL_FLYING|SPIN_ATTACK|SLEEPING|SITTING` — carries forward; snaps at frame start (no interpolation). Mannequin: invalid poses fall back to STANDING (`Mannequin.validPoses()`).
- `use=main|off|none` — carries forward. Sets the LivingEntity "using item" flags through `Packets.useItem(handle, using, offHand)` (NMS `DATA_LIVING_ENTITY_FLAGS`). With the right held item the skinned model shows the vanilla arm pose: bow → aim both arms, crossbow → charge, trident → arm raised overhead, spyglass → arm to eye, goat_horn → horn to mouth, shield (off/main) → block, food/potion → eat/drink.
- `item=<material|none>` / `offitem=<material|none>` — carries forward; overrides the main/off-hand item shown (equipment packet), `none` = back to the saved equipment. Invalid materials ignored.
- `swing=main|off` — one-shot arm swing at the frame start (`Packets.swing`), not carried.
- Limbs (`head body larm rarm lleg rleg`) unchanged: full limbs on ARMOR_STAND, head/body turning on other types.
Pose/use/item tokens apply to every NPC type (armor stands ignore `use`).

## B. Skin palette (new class `net.spxry.nexora.npc.SkinPalette`, exposed as `plugin.skins()`)
- `public record Palette(org.bukkit.Color torso, org.bukkit.Color arms, org.bukkit.Color legs, org.bukkit.Color feet)`
- `public SkinPalette(Nexora plugin)`; `Optional<Palette> cached(String skinId)`; `CompletableFuture<Optional<Palette>> request(String skinId)` (dedupes in-flight, caches results incl. failures for `npc.skin-palette.retry-minutes`).
- skinId = `Npc.skinId()` (texture hash if 32+ hex chars, else player name). URLs from config `npc.skin-palette.texture-url` (`http://textures.minecraft.net/texture/{hash}`) and `npc.skin-palette.name-url` (`https://mc-heads.net/skin/{name}`). Fetch on `plugin.scheduler().runAsync` with java.net.http.HttpClient (timeout `npc.skin-palette.timeout-seconds`), decode with `javax.imageio.ImageIO`, sample average opaque colour of standard 64x64 skin regions (torso front 20-28×20-32 + jacket overlay 20-28×36-48 if opaque, arm 44-48×20-32, leg 4-8×20-28, feet 4-8×28-32; handle legacy 64x32 by skipping overlays).
- Npc (ARMOR_STAND with a skin): for each empty CHEST/LEGS/FEET slot use LEATHER_CHESTPLATE/LEGGINGS/BOOTS dyed with palette torso/legs/feet via `DataComponentTypes.DYED_COLOR` (`DyedItemColor.dyedItemColor(color)`) — never mutate the saved equipment map. If not cached at build: `plugin.skins().request(id)` then on success `plugin.objects().mutate(this, n -> {})` once to rebuild. Config `npc.skin-palette.enabled: true`.
