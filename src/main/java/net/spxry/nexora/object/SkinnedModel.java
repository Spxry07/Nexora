package net.spxry.nexora.object;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.nms.Packets;
import net.spxry.nexora.npc.SkinParts.Part;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class SkinnedModel {
    record Spec(Location anchor, Map<Part, ProfileProperty> parts, ResolvableProfile head, boolean slim,
                double scale, double viewRange, String glowColor) {}

    private enum Group {
        HEAD(true, 1f, 1f, 0f, SkinnedModel.NECK_Y),
        BODY(false, 1f, 1f, 0f, SkinnedModel.BODY_PIVOT_Y),
        RIGHT_ARM(true, -1f, -1f, -SkinnedModel.SHOULDER_X, SkinnedModel.SHOULDER_Y),
        LEFT_ARM(true, -1f, 1f, SkinnedModel.SHOULDER_X, SkinnedModel.SHOULDER_Y),
        RIGHT_LEG(false, -1f, -1f, -SkinnedModel.HIP_X, SkinnedModel.HIP_Y),
        LEFT_LEG(false, -1f, 1f, SkinnedModel.HIP_X, SkinnedModel.HIP_Y);

        final boolean childOfBody;
        final float pitchSign;
        final float rollSign;
        final Vector3f joint;

        Group(boolean childOfBody, float pitchSign, float rollSign, float x, float y) {
            this.childOfBody = childOfBody;
            this.pitchSign = pitchSign;
            this.rollSign = rollSign;
            this.joint = new Vector3f(x, y, 0f);
        }
    }

    private record Slot(Group group, float w, float h, float d, float cx, float cy) {}

    private static final class Node {
        final ItemDisplay display;
        final Entity handle;
        final Slot slot;
        float[] last = new float[0];
        boolean moved;
        ItemStack item = new ItemStack(Material.AIR);
        boolean shown;

        Node(ItemDisplay display, Slot slot) {
            this.display = display;
            this.handle = Packets.handle(display);
            this.slot = slot;
        }
    }

    private static final float NECK_Y = 24f;
    private static final float BODY_PIVOT_Y = 12f;
    private static final float SHOULDER_X = 6f;
    private static final float SHOULDER_Y = 22f;
    private static final float HIP_X = 2f;
    private static final float HIP_Y = 12f;
    private static final float HEAD_EDGE = 8f;
    private static final float TORSO_WIDTH = 8f;
    private static final float HALF_HEIGHT = 6f;
    private static final float LIMB_DEPTH = 4f;
    private static final float ARM_WIDTH = 4f;
    private static final float SLIM_ARM_WIDTH = 3f;
    private static final float LEG_WIDTH = 4f;
    private static final float SLIM_SHIFT = 0.5f;
    private static final float HEAD_CENTRE = 4f;
    private static final float TORSO_UPPER_CENTRE = 9f;
    private static final float TORSO_LOWER_CENTRE = 3f;
    private static final float ARM_UPPER_CENTRE = -1f;
    private static final float ARM_LOWER_CENTRE = -7f;
    private static final float LEG_UPPER_CENTRE = -3f;
    private static final float LEG_LOWER_CENTRE = -9f;
    private static final float PIXELS_PER_BLOCK = 16f;
    private static final float HAND_PITCH = -90f;
    private static final float HAND_YAW = 180f;
    private static final int MAX_TELEPORT_DURATION = 59;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final float HALF_TURN = 180f;
    private static final float FULL_TURN = 360f;
    private static final double MOVE_EPSILON = 1.0E-4;
    private static final float BLEND_EPSILON = 0.01f;
    private static final float LOOK_SNAP = 0.05f;
    private static final int TRANSFORM_FLOATS = 10;

    private static final String CONFIG = "model.";
    private static final String ITEM_TRANSFORM_KEY = CONFIG + "item-transform";
    private static final String CUBE_SIZE_KEY = CONFIG + "cube-size";
    private static final String CUBE_OFFSET_KEY = CONFIG + "cube-offset-y";
    private static final String PART_YAW_KEY = CONFIG + "part-yaw";
    private static final String HITBOX_WIDTH_KEY = CONFIG + "hitbox-width";
    private static final String HITBOX_HEIGHT_KEY = CONFIG + "hitbox-height";
    private static final String HAND_MAIN_KEY = CONFIG + "hand-transform-main";
    private static final String HAND_OFF_KEY = CONFIG + "hand-transform-off";
    private static final String HAND_X_KEY = CONFIG + "hand-offset-x";
    private static final String HAND_Y_KEY = CONFIG + "hand-offset-y";
    private static final String HAND_Z_KEY = CONFIG + "hand-offset-z";
    private static final String WALK_SWING_KEY = CONFIG + "walk-swing";
    private static final String WALK_PHASE_KEY = CONFIG + "walk-phase-per-block";
    private static final String WALK_BLEND_KEY = CONFIG + "walk-blend";
    private static final String LOOK_YAW_KEY = CONFIG + "look-max-yaw";
    private static final String LOOK_PITCH_KEY = CONFIG + "look-max-pitch";
    private static final String LOOK_SMOOTH_KEY = CONFIG + "look-smoothing";
    private static final String SWING_ANGLE_KEY = CONFIG + "swing-angle";
    private static final String SWING_TICKS_KEY = CONFIG + "swing-ticks";
    private static final String INTERVAL_KEY = "animation.interval-ticks";
    private static final String VIEW_UNIT_KEY = "display.view-range-unit";

    private static final double DEFAULT_CUBE_SIZE = 0.5;
    private static final double DEFAULT_CUBE_OFFSET = -0.25;
    private static final double DEFAULT_HITBOX_WIDTH = 0.6;
    private static final double DEFAULT_HITBOX_HEIGHT = 1.8;
    private static final double DEFAULT_HAND_X = 0.0;
    private static final double DEFAULT_HAND_Y = -0.125;
    private static final double DEFAULT_HAND_Z = 0.625;
    private static final double DEFAULT_WALK_SWING = 30.0;
    private static final double DEFAULT_WALK_PHASE = 2.66;
    private static final double DEFAULT_WALK_BLEND = 0.35;
    private static final double DEFAULT_LOOK_YAW = 70.0;
    private static final double DEFAULT_LOOK_PITCH = 40.0;
    private static final double DEFAULT_LOOK_SMOOTH = 0.4;
    private static final double DEFAULT_SWING_ANGLE = 80.0;
    private static final int DEFAULT_SWING_TICKS = 6;
    private static final double DEFAULT_VIEW_UNIT = 64.0;
    private static final String DEFAULT_ITEM_TRANSFORM = "NONE";
    private static final String DEFAULT_HAND_MAIN = "THIRDPERSON_RIGHTHAND";
    private static final String DEFAULT_HAND_OFF = "THIRDPERSON_LEFTHAND";

    private static final Quaternionf HAND_FRAME = new Quaternionf()
        .rotateX((float) Math.toRadians(HAND_PITCH))
        .rotateY((float) Math.toRadians(HAND_YAW));

    private final float unit;
    private final float cubeSize;
    private final float cubeOffset;
    private final Quaternionf faceFix;
    private final Vector3f handOffset;
    private final float scale;
    private final float walkSwing;
    private final float walkPhaseRate;
    private final float walkBlendRate;
    private final float lookMaxYaw;
    private final float lookMaxPitch;
    private final float lookSmooth;
    private final float swingAngle;
    private final int swingTicks;
    private final List<Node> pieces = new ArrayList<>();
    private final List<Node> all = new ArrayList<>();
    private final List<Entity> handles = new ArrayList<>();
    private final Node mainHand;
    private final Node offHand;
    private final Interaction hitbox;
    private final float[] work = new float[Keyframes.SIZE];
    private final float[] lastAngles = new float[Keyframes.SIZE];
    private boolean posed;
    private boolean refresh;
    private float lastYaw;
    private float walkPhase;
    private float walkBlend;
    private float lookYaw;
    private float lookPitch;
    private int mainSwing;
    private int offSwing;
    private boolean glowing;

    SkinnedModel(Nexora plugin, Spec spec) {
        var config = plugin.getConfig();
        var world = spec.anchor().getWorld();
        if (world == null) throw new IllegalStateException(spec.anchor().toString());
        scale = (float) spec.scale();
        unit = scale / PIXELS_PER_BLOCK;
        cubeSize = (float) config.getDouble(CUBE_SIZE_KEY, DEFAULT_CUBE_SIZE);
        cubeOffset = (float) config.getDouble(CUBE_OFFSET_KEY, DEFAULT_CUBE_OFFSET);
        faceFix = new Quaternionf().rotationY((float) Math.toRadians(config.getDouble(PART_YAW_KEY, 0)));
        handOffset = new Vector3f(
            (float) config.getDouble(HAND_X_KEY, DEFAULT_HAND_X),
            (float) config.getDouble(HAND_Y_KEY, DEFAULT_HAND_Y),
            (float) config.getDouble(HAND_Z_KEY, DEFAULT_HAND_Z)).mul(scale);
        walkSwing = (float) config.getDouble(WALK_SWING_KEY, DEFAULT_WALK_SWING);
        walkPhaseRate = (float) config.getDouble(WALK_PHASE_KEY, DEFAULT_WALK_PHASE);
        walkBlendRate = (float) config.getDouble(WALK_BLEND_KEY, DEFAULT_WALK_BLEND);
        lookMaxYaw = (float) config.getDouble(LOOK_YAW_KEY, DEFAULT_LOOK_YAW);
        lookMaxPitch = (float) config.getDouble(LOOK_PITCH_KEY, DEFAULT_LOOK_PITCH);
        lookSmooth = (float) config.getDouble(LOOK_SMOOTH_KEY, DEFAULT_LOOK_SMOOTH);
        swingAngle = (float) config.getDouble(SWING_ANGLE_KEY, DEFAULT_SWING_ANGLE);
        swingTicks = Math.max(1, config.getInt(SWING_TICKS_KEY, DEFAULT_SWING_TICKS));
        int interval = Math.max(1, config.getInt(INTERVAL_KEY, 2));
        float range = (float) (spec.viewRange() / config.getDouble(VIEW_UNIT_KEY, DEFAULT_VIEW_UNIT));
        var color = glowColor(spec.glowColor());
        var itemTransform = parse(ItemDisplay.ItemDisplayTransform.class, config.getString(ITEM_TRANSFORM_KEY, DEFAULT_ITEM_TRANSFORM), ItemDisplay.ItemDisplayTransform.NONE);
        var anchor = spec.anchor();
        for (var entry : slots(spec.slim()).entrySet()) {
            var property = spec.parts().get(entry.getKey());
            var profile = entry.getKey() == Part.HEAD || property == null ? spec.head()
                : ResolvableProfile.resolvableProfile().addProperty(property).build();
            var stack = new ItemStack(Material.PLAYER_HEAD);
            stack.setData(DataComponentTypes.PROFILE, profile);
            var display = display(world, anchor, interval, range, color);
            display.setItemStack(stack);
            display.setItemDisplayTransform(itemTransform);
            pieces.add(new Node(display, entry.getValue()));
        }
        mainHand = hand(world, anchor, interval, range, color, config.getString(HAND_MAIN_KEY, DEFAULT_HAND_MAIN), ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND, Group.RIGHT_ARM);
        offHand = hand(world, anchor, interval, range, color, config.getString(HAND_OFF_KEY, DEFAULT_HAND_OFF), ItemDisplay.ItemDisplayTransform.THIRDPERSON_LEFTHAND, Group.LEFT_ARM);
        all.addAll(pieces);
        all.add(mainHand);
        all.add(offHand);
        hitbox = world.createEntity(anchor, Interaction.class);
        hitbox.setInteractionWidth((float) (config.getDouble(HITBOX_WIDTH_KEY, DEFAULT_HITBOX_WIDTH) * scale));
        hitbox.setInteractionHeight((float) (config.getDouble(HITBOX_HEIGHT_KEY, DEFAULT_HITBOX_HEIGHT) * scale));
        hitbox.setResponsive(true);
        for (var node : all) handles.add(node.handle);
        handles.add(Packets.handle(hitbox));
    }

    private static Map<Part, Slot> slots(boolean slim) {
        float armWidth = slim ? SLIM_ARM_WIDTH : ARM_WIDTH;
        float shift = slim ? SLIM_SHIFT : 0f;
        Map<Part, Slot> map = new EnumMap<>(Part.class);
        map.put(Part.HEAD, new Slot(Group.HEAD, HEAD_EDGE, HEAD_EDGE, HEAD_EDGE, 0f, HEAD_CENTRE));
        map.put(Part.TORSO_UPPER, new Slot(Group.BODY, TORSO_WIDTH, HALF_HEIGHT, LIMB_DEPTH, 0f, TORSO_UPPER_CENTRE));
        map.put(Part.TORSO_LOWER, new Slot(Group.BODY, TORSO_WIDTH, HALF_HEIGHT, LIMB_DEPTH, 0f, TORSO_LOWER_CENTRE));
        map.put(Part.RIGHT_ARM_UPPER, new Slot(Group.RIGHT_ARM, armWidth, HALF_HEIGHT, LIMB_DEPTH, shift, ARM_UPPER_CENTRE));
        map.put(Part.RIGHT_ARM_LOWER, new Slot(Group.RIGHT_ARM, armWidth, HALF_HEIGHT, LIMB_DEPTH, shift, ARM_LOWER_CENTRE));
        map.put(Part.LEFT_ARM_UPPER, new Slot(Group.LEFT_ARM, armWidth, HALF_HEIGHT, LIMB_DEPTH, -shift, ARM_UPPER_CENTRE));
        map.put(Part.LEFT_ARM_LOWER, new Slot(Group.LEFT_ARM, armWidth, HALF_HEIGHT, LIMB_DEPTH, -shift, ARM_LOWER_CENTRE));
        map.put(Part.RIGHT_LEG_UPPER, new Slot(Group.RIGHT_LEG, LEG_WIDTH, HALF_HEIGHT, LIMB_DEPTH, 0f, LEG_UPPER_CENTRE));
        map.put(Part.RIGHT_LEG_LOWER, new Slot(Group.RIGHT_LEG, LEG_WIDTH, HALF_HEIGHT, LIMB_DEPTH, 0f, LEG_LOWER_CENTRE));
        map.put(Part.LEFT_LEG_UPPER, new Slot(Group.LEFT_LEG, LEG_WIDTH, HALF_HEIGHT, LIMB_DEPTH, 0f, LEG_UPPER_CENTRE));
        map.put(Part.LEFT_LEG_LOWER, new Slot(Group.LEFT_LEG, LEG_WIDTH, HALF_HEIGHT, LIMB_DEPTH, 0f, LEG_LOWER_CENTRE));
        return map;
    }

    private static Node hand(World world, Location anchor, int interval, float range, Color color, String name,
                      ItemDisplay.ItemDisplayTransform fallback, Group arm) {
        var display = display(world, anchor, interval, range, color);
        display.setItemStack(new ItemStack(Material.AIR));
        display.setItemDisplayTransform(parse(ItemDisplay.ItemDisplayTransform.class, name, fallback));
        return new Node(display, new Slot(arm, 0f, 0f, 0f, 0f, 0f));
    }

    private static ItemDisplay display(World world, Location anchor, int interval, float range, Color color) {
        var display = world.createEntity(anchor, ItemDisplay.class);
        display.setBillboard(Display.Billboard.FIXED);
        display.setInterpolationDuration(interval);
        display.setTeleportDuration(Math.min(interval, MAX_TELEPORT_DURATION));
        display.setViewRange(range);
        if (color != null) display.setGlowColorOverride(color);
        return display;
    }

    private static Color glowColor(String name) {
        if (name == null || name.isBlank()) return null;
        var format = ChatFormatting.getByName(name);
        if (format == null || !format.isColor() || format.getColor() == null) return null;
        return Color.fromRGB(format.getColor() & RGB_MASK);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    void glow(boolean on) {
        glowing = on;
        for (var node : all) node.display.setGlowing(on);
    }

    boolean glowing() {
        return glowing;
    }

    void items(ItemStack main, ItemStack off) {
        refresh |= setItem(mainHand, main);
        refresh |= setItem(offHand, off);
    }

    private static boolean setItem(Node hand, ItemStack item) {
        var next = item == null ? new ItemStack(Material.AIR) : item;
        if (Objects.equals(hand.item, next)) return false;
        hand.item = next.clone();
        hand.shown = !next.isEmpty();
        hand.display.setItemStack(hand.item);
        return true;
    }

    void swing(boolean offHandSwing) {
        if (offHandSwing) offSwing = swingTicks;
        else mainSwing = swingTicks;
    }

    int[] ids() {
        return handles.stream().mapToInt(Entity::getId).toArray();
    }

    Packet<?> destroy() {
        return Packets.destroy(ids());
    }

    List<Packet<?>> spawn(double x, double y, double z) {
        List<Packet<?>> list = new ArrayList<>();
        for (var handle : handles) {
            list.add(Packets.spawn(handle, x, y, z, 0f, 0f));
            list.add(Packets.fullData(handle));
        }
        for (var handle : handles) Packets.clearDirty(handle);
        return list;
    }

    List<Packet<?>> teleport(double x, double y, double z) {
        List<Packet<?>> list = new ArrayList<>();
        for (var handle : handles) list.add(Packets.teleport(handle.getId(), x, y, z, 0f, 0f));
        return list;
    }

    List<Packet<?>> catchUp(double x, double y, double z) {
        List<Packet<?>> list = teleport(x, y, z);
        for (var handle : handles) list.add(Packets.fullData(handle));
        return list;
    }

    List<Packet<?>> tick(float[] base, float yaw, double moved, double[] target, int interval) {
        System.arraycopy(base, 0, work, 0, Keyframes.SIZE);
        walk(moved);
        mainSwing = advance(mainSwing, Keyframes.RIGHT_ARM, interval);
        offSwing = advance(offSwing, Keyframes.LEFT_ARM, interval);
        look(target, yaw);
        return pose(work, yaw);
    }

    List<Packet<?>> pose(float[] angles, float yaw) {
        boolean changed = !posed || yaw != lastYaw || !Arrays.equals(angles, lastAngles);
        if (changed || refresh) {
            System.arraycopy(angles, 0, lastAngles, 0, Keyframes.SIZE);
            lastYaw = yaw;
            posed = true;
            refresh = false;
            layout(angles, yaw);
        }
        List<Packet<?>> out = new ArrayList<>();
        for (var node : all) {
            var data = Packets.dirtyData(node.handle, node.moved);
            node.moved = false;
            if (data != null) out.add(data);
        }
        return out;
    }

    private void walk(double moved) {
        boolean walking = moved > MOVE_EPSILON;
        walkPhase = (float) ((walkPhase + moved * walkPhaseRate) % Math.TAU);
        walkBlend += ((walking ? 1f : 0f) - walkBlend) * walkBlendRate;
        if (walkBlend < BLEND_EPSILON) {
            walkBlend = 0f;
            return;
        }
        float swing = walkSwing * walkBlend * (float) Math.sin(walkPhase);
        work[Keyframes.RIGHT_LEG + Keyframes.PITCH] += swing;
        work[Keyframes.LEFT_LEG + Keyframes.PITCH] -= swing;
        work[Keyframes.RIGHT_ARM + Keyframes.PITCH] -= swing;
        work[Keyframes.LEFT_ARM + Keyframes.PITCH] += swing;
    }

    private int advance(int remaining, int offset, int interval) {
        if (remaining <= 0) return 0;
        float progress = 1f - (float) remaining / swingTicks;
        work[offset + Keyframes.PITCH] += swingAngle * (float) Math.sin(Math.PI * progress);
        return Math.max(0, remaining - interval);
    }

    private void look(double[] target, float yaw) {
        float wantYaw = 0f;
        float wantPitch = 0f;
        if (target != null) {
            double horizontal = Math.hypot(target[0], target[2]);
            float toYaw = (float) Math.toDegrees(Math.atan2(-target[0], target[2]));
            float toPitch = (float) -Math.toDegrees(Math.atan2(target[1], horizontal));
            float relative = wrap(toYaw - yaw - work[Keyframes.BODY_YAW] - work[Keyframes.HEAD_YAW]);
            wantYaw = Math.clamp(relative, -lookMaxYaw, lookMaxYaw);
            wantPitch = Math.clamp(toPitch - work[Keyframes.HEAD_PITCH], -lookMaxPitch, lookMaxPitch);
        }
        lookYaw = approach(lookYaw, wantYaw);
        lookPitch = approach(lookPitch, wantPitch);
        work[Keyframes.HEAD_YAW] += lookYaw;
        work[Keyframes.HEAD_PITCH] += lookPitch;
    }

    private float approach(float current, float wanted) {
        float difference = wanted - current;
        return Math.abs(difference) < LOOK_SNAP ? wanted : current + difference * lookSmooth;
    }

    private static float wrap(float degrees) {
        float turned = (degrees + HALF_TURN) % FULL_TURN;
        if (turned < 0f) turned += FULL_TURN;
        return turned - HALF_TURN;
    }

    private static Quaternionf limb(Group group, float[] angles) {
        int offset = group.ordinal() * Keyframes.AXES;
        float pitch = (float) Math.toRadians(angles[offset + Keyframes.PITCH]) * group.pitchSign;
        float yaw = (float) -Math.toRadians(angles[offset + Keyframes.YAW]);
        float roll = (float) Math.toRadians(angles[offset + Keyframes.ROLL]) * group.rollSign;
        return new Quaternionf().rotateZ(roll).rotateY(yaw).rotateX(pitch);
    }

    private void layout(float[] angles, float yawDegrees) {
        var yaw = new Quaternionf().rotationY((float) -Math.toRadians(yawDegrees));
        var groups = Group.values();
        var locals = new Quaternionf[groups.length];
        var joints = new Vector3f[groups.length];
        var body = limb(Group.BODY, angles);
        var pivot = Group.BODY.joint;
        for (var group : groups) {
            var rotation = limb(group, angles);
            var joint = new Vector3f(group.joint);
            if (group.childOfBody) {
                rotation = new Quaternionf(body).mul(rotation);
                joint.sub(pivot);
                body.transform(joint);
                joint.add(pivot);
            }
            locals[group.ordinal()] = rotation;
            joints[group.ordinal()] = joint;
        }
        for (var node : pieces) place(node, yaw, locals, joints);
        placeHand(mainHand, yaw, locals, joints, false);
        placeHand(offHand, yaw, locals, joints, true);
    }

    private void place(Node node, Quaternionf yaw, Quaternionf[] locals, Vector3f[] joints) {
        var slot = node.slot;
        int index = slot.group().ordinal();
        var local = locals[index];
        var total = new Quaternionf(yaw).mul(local).mul(faceFix);
        var centre = new Vector3f(slot.cx(), slot.cy(), 0f);
        local.transform(centre).add(joints[index]).mul(unit);
        yaw.transform(centre);
        var size = new Vector3f(slot.w(), slot.h(), slot.d()).mul(unit / cubeSize);
        var offset = new Vector3f(0f, size.y * cubeOffset, 0f);
        total.transform(offset);
        centre.sub(offset);
        apply(node, centre, total, size);
    }

    private void placeHand(Node hand, Quaternionf yaw, Quaternionf[] locals, Vector3f[] joints, boolean left) {
        if (!hand.shown) return;
        int index = hand.slot.group().ordinal();
        var total = new Quaternionf(yaw).mul(locals[index]).mul(HAND_FRAME);
        var shoulder = new Vector3f(joints[index]).mul(unit);
        yaw.transform(shoulder);
        var offset = new Vector3f(left ? -handOffset.x : handOffset.x, handOffset.y, handOffset.z);
        total.transform(offset);
        shoulder.add(offset);
        apply(hand, shoulder, total, new Vector3f(scale, scale, scale));
    }

    private static void apply(Node node, Vector3f translation, Quaternionf rotation, Vector3f size) {
        float[] next = new float[TRANSFORM_FLOATS];
        next[0] = translation.x;
        next[1] = translation.y;
        next[2] = translation.z;
        next[3] = rotation.x;
        next[4] = rotation.y;
        next[5] = rotation.z;
        next[6] = rotation.w;
        next[7] = size.x;
        next[8] = size.y;
        next[9] = size.z;
        if (Arrays.equals(next, node.last)) return;
        node.last = next;
        node.display.setTransformation(new Transformation(translation, rotation, size, new Quaternionf()));
        node.moved = true;
    }
}
