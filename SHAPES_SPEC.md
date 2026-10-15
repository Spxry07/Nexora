# Solid hologram shapes (line type SHAPE)

Goal: a hologram line can be a real solid shape (triangle, star, cube...) with a color, a color effect and every hologram animation.

## Rendering
- Each piece is a TextDisplay with text " " and a background color. Vanilla draws the background of " " as a quad in display space: x in [-0.05, 0.075], y in [0, 0.25], z = 0, facing +Z (counter-clockwise from +Z).
- An affine transform (Display transformation, stored as T * L * S * R) maps that quad to any 3D parallelogram (P0, E1, E2):
  linear columns = E1/0.125, E2/0.25, unit normal; translation = P0 - lin * (-0.05, 0, 0).
  The SVD is done once at build time (com.mojang.math.Transformation). Each tick uses: t' = G(f t), L' = G L, S' = f S, R' = R, where G = spin/sway/rot and f = pulse. No SVD per tick.
- Parallelogram faces become 1 piece. Other convex faces are fan-triangulated, and each triangle becomes 3 corner parallelograms at half size. Their union is the exact triangle, and they overlap only in its middle (invisible with opaque colors).
- Outline style (2D): each edge becomes a strip of `shape-thickness`, extended by half the thickness at both ends.
- 2D shapes are double-sided (`shapes.double-sided`): a second piece with E1/E2 swapped. 3D solids only face outward.
- 3D faces are shaded by normal (`shapes.shade.*`).

## Properties (line)
- `type: SHAPE`
- `shape`: TRIANGLE, RIGHT_TRIANGLE, SQUARE, DIAMOND, POLYGON, CIRCLE, STAR, HEART, CROSS, ARROW, CUBE, PYRAMID, PRISM, OCTAHEDRON, CYLINDER, CONE
- `shape-style`: FILL | OUTLINE (2D only)
- `shape-sides` (polygon/star points/cylinder/cone), `shape-inner` (star), `shape-thickness` (outline)
- Size uses the existing scale and scale-x/y/z (base shape fits a 1 block box, centered on the line position).
- Color: `color-a` (#AARRGGBB allowed) and effect FLICKER/FADE/RAINBOW/WAVE (A to B)/BLINK, with `effect-speed`.
- Billboard and rot-x/y/z work as on other lines (FIXED suggested for 3D).

## Limits
`shapes.max-pieces` caps the entities per shape line. `shapes.circle-segments` and `shapes.heart-segments` set curve detail.
