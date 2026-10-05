# Animation & Hologram Styling Spec (round 4)

Props already added to `editor.yml` / `config.yml defaults` (the lead owns those files — do not edit them).

## NPC keyframes (`npc` props)
- `animation-enabled` (bool), `animation-speed` (0.1–5 multiplier), `small` (bool, armor stand only), `animation` (multiline text).
- Format: one frame per line: `<ticks> <limb>=<x>,<y>,<z> ...` — limbs `head body larm rarm lleg rleg`, angles in DEGREES (Minecraft armor-stand euler: x = pitch forward/back, y = yaw twist, z = roll sideways). Blank lines and malformed tokens are skipped. A limb missing from a frame keeps its value from the previous frame. Initial values = armor stand defaults: head 0,0,0 · body 0,0,0 · larm -10,0,-10 · rarm -15,0,10 · lleg -1,0,-1 · rleg 1,0,1. Frame ticks clamped to 1..1200, max 256 frames. The sequence loops; it interpolates linearly from each frame to the next over that frame's ticks (last frame interpolates back to the first).
- Playback: elapsed animation ticks = `ticks * animation-speed`.
- ARMOR_STAND NPCs ("Poseable"): apply all six limbs through Paper API `ArmorStand#setHeadPose/setBodyPose/setLeftArmPose/setRightArmPose/setLeftLegPose/setRightLegPose(EulerAngle radians)` on the template, then `Packets.dirtyData`. Armor stand template also: `setArms(true)`, `setBasePlate(false)`, `setSmall(small)`, `setGravity(false)`. If HEAD equipment slot is empty and a skin is set (`skinValue`/`skinName`), send a PLAYER_HEAD in the HEAD equipment packet carrying the NPC profile (`ItemStack#setData(DataComponentTypes.PROFILE, profile())`) so the stand wears the skin's head.
- Other NPC types (MANNEQUIN, mobs): only `head` (x = pitch, y = yaw offset added to body yaw) and `body` y (added to body yaw) are applied, via rotation/head packets to viewers not in the look-at set; when look-at is active for a viewer, look-at wins.

## Hologram lines (`line` props)
- `offset-x`, `offset-z` (-10..10 blocks): added to the line position, rotated by the hologram anchor yaw (x = right, z = forward) for every layout.
- `rot-x`, `rot-y`, `rot-z` (degrees): static rotation applied in the line Transformation's left rotation before spin/sway (`new Quaternionf().rotationY(spin).rotateZ(sway).rotateY(rotY).rotateX(rotX).rotateZ(rotZ)`). Text with billboard FIXED and rot-x -90 lies flat on the ground.
- New effects: `SOLID` (strip all colour codes, colour every visible char with `color-a`; keep bold/italic etc. codes), `FLICKER` (whole text in `color-a`, switching to `color-b` on steps where a deterministic hash of (step, line identity) < `effects.flicker-chance`), `FADE` (whole text colour = smooth lerp A↔B using `0.5 + 0.5*sin(step * effects.fade-step * TAU)`).

## Web (index.html) — see web agent prompt.
