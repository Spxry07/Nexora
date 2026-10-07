package net.spxry.nexora.object;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.spxry.nexora.npc.HasRuntime;
import net.spxry.nexora.npc.SkinParts;
import net.spxry.nexora.npc.TriggerHost;
import net.spxry.nexora.npc.TriggerRuntime;
import net.spxry.nexora.npc.Triggers;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.edit.Binding;
import net.spxry.nexora.nms.Packets;
import net.spxry.nexora.util.ColorUtil;
import net.spxry.nexora.util.ItemCodec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class Npc extends NexoraObject implements TriggerHost, HasRuntime {
    private enum ClickType { ANY, RIGHT, LEFT }

    private static final String LOOK_HOLD_KEY = "npc.triggers.look-hold-ticks";
    private static final long DEFAULT_LOOK_HOLD = 40L;
    private static final double FEET_LIFT = 0.1;
    private static final double CENTRE_FACTOR = 0.5;
    private static final double BODY_FACTOR = 0.6;
    private static final double HAND_FACTOR = 0.75;
    private static final double HAND_SIDE = 0.35;
    private static final double HAND_FORWARD = 0.2;
    private static final double TOP_LIFT = 0.25;
    private static final double ABOVE_LIFT = 0.5;
    private static final int RESET_FRAME = -2;
    private static final String ANCHOR_FEET = "feet";
    private static final String ANCHOR_BOTTOM = "bottom";
    private static final String ANCHOR_TOP = "top";
    private static final String ANCHOR_ABOVE = "above";
    private static final String ANCHOR_HAND = "hand";
    private static final String ANCHOR_OFFHAND = "offhand";
    private static final String VERB_SWING = "swing";
    private static final String VERB_GLOW = "glow";
    private static final String VERB_ANIMATE = "animate";
    private static final String VERB_ITEM = "item";
    private static final String VERB_LOOK = "look";
    private static final String VERB_HURT = "hurt";
    private static final String VERB_ANIMATION = "animation";
    private static final String ARG_MAIN = "main";
    private static final String ARG_OFF = "off";
    private static final String ARG_ON = "on";
    private static final String ARG_TOGGLE = "toggle";
    private static final String ARG_RESTART = "restart";
    private static final String ARG_NONE = "none";
    private static final String ARG_AIR = "air";

    private static final int TICKS_PER_SECOND = 20;
    private static final long MILLIS_PER_TICK = 50L;
    private static final double PARTICLE_HEIGHT = 1.0;
    private static final double UNIT_SCALE = 1.0;
    private static final float NO_PITCH = 0F;
    private static final double DEFAULT_SPEED = 1.0;
    private static final String TEAM_PREFIX = "nx";
    private static final String TEXTURES = "textures";
    private static final Pattern TEXTURE_HASH = Pattern.compile("texture/([0-9a-fA-F]{32,})");
    private static final String EXTRA_EQUIPMENT = "equipment";
    private static final String DEFAULT_TYPE_KEY = "npc.default-type";
    private static final String ANIMATIONS_KEY = "npc.animations.";
    private static final String ANIMATION_NONE = "NONE";
    private static final String MODEL_TYPE = "MODEL";
    private static final String MODEL_EYE_KEY = "model.eye-height";
    private static final double DEFAULT_MODEL_EYE = 1.62;
    private static final String AXIS_SPLIT = ",";
    private static final Set<SkinParts.Part> REQUIRED_PARTS = EnumSet.complementOf(EnumSet.of(SkinParts.Part.HEAD));

    private record Focus(double x, double y, double z, long until) {}

    public static final Map<String, Binding<Npc>> BINDINGS = bindings();

    private final Map<EquipmentSlot, ItemStack> equipment = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> looking = new ConcurrentHashMap<>();
    private final TriggerRuntime runtime;
    private final Predicate<Material> holdingTest = material -> heldItem() == material;
    private volatile Material itemOverride;
    private volatile boolean animationPaused;
    private double animationClock;
    private String triggers = "";
    private volatile double lookTriggerRange;
    private volatile double approachRange;
    private volatile boolean nameplate;
    private volatile double nameplateHeight;

    private String entityType;
    private String name = "";
    private boolean nameVisible;
    private String description = "";
    private String skinName = "";
    private String skinValue = "";
    private String skinSignature = "";
    private String pose = "";
    private boolean glow;
    private String glowColor = "";
    private double scale = UNIT_SCALE;
    private volatile boolean lookAt;
    private volatile double lookRange;
    private double spinSpeed;
    private int swingInterval;
    private int sneakInterval;
    private int hurtInterval;
    private String actions = "";
    private String clickType = "";
    private int cooldown;
    private double animationSpeed = DEFAULT_SPEED;
    private String animation = ANIMATION_NONE;
    private String poseHead = "";
    private String poseBody = "";
    private String poseRightArm = "";
    private String poseLeftArm = "";
    private String poseRightLeg = "";
    private String poseLeftLeg = "";

    private volatile SkinnedModel model;
    private volatile String requestedSkin = "";
    private volatile Focus focus;
    private final Map<UUID, double[]> lookers = new ConcurrentHashMap<>();
    private final float[] staticPose = new float[Keyframes.SIZE];
    private final float[] angles = new float[Keyframes.SIZE];
    private volatile LivingEntity template;
    private volatile Entity handle;
    private volatile int entityId;
    private volatile float currentYaw;
    private volatile double eyeHeight;
    private volatile Location base;
    private Pose basePose = Pose.STANDING;
    private boolean sneaking;
    private volatile Keyframes keyframes;
    private volatile float bodyOffset;
    private volatile float headOffset;
    private volatile float headPitch;
    private long rotationKey;
    private int lastFrame = -1;
    private boolean itemsChanged;
    private Keyframes.State state = Keyframes.State.NONE;
    private final float[] sampled = new float[Keyframes.SIZE];

    public Npc(Nexora plugin, String id, Location anchor) {
        super(plugin, id, anchor);
        this.entityType = plugin.getConfig().getString(DEFAULT_TYPE_KEY, "");
        this.base = anchor.clone();
        this.currentYaw = anchor.getYaw();
        this.runtime = new TriggerRuntime(plugin, this);
    }

    private static Map<String, Binding<Npc>> bindings() {
        Map<String, Binding<Npc>> map = commonBindings();
        map.put("entity-type", Binding.text(n -> n.entityType, (n, v) -> n.entityType = v));
        map.put("name", Binding.text(n -> n.name, (n, v) -> n.name = v));
        map.put("name-visible", Binding.bool(n -> n.nameVisible, (n, v) -> n.nameVisible = v));
        map.put("description", Binding.text(n -> n.description, (n, v) -> n.description = v));
        map.put("skin-name", Binding.text(n -> n.skinName, (n, v) -> n.skinName = v));
        map.put("skin-value", Binding.text(n -> n.skinValue, (n, v) -> n.skinValue = v));
        map.put("skin-signature", Binding.text(n -> n.skinSignature, (n, v) -> n.skinSignature = v));
        map.put("pose", Binding.text(n -> n.pose, (n, v) -> n.pose = v));
        map.put("glow", Binding.bool(n -> n.glow, (n, v) -> n.glow = v));
        map.put("glow-color", Binding.text(n -> n.glowColor, (n, v) -> n.glowColor = v));
        map.put("scale", Binding.number(n -> n.scale, (n, v) -> n.scale = v));
        map.put("look-at", Binding.bool(n -> n.lookAt, (n, v) -> n.lookAt = v));
        map.put("look-range", Binding.number(n -> n.lookRange, (n, v) -> n.lookRange = v));
        map.put("spin-speed", Binding.number(n -> n.spinSpeed, (n, v) -> n.spinSpeed = v));
        map.put("swing-interval", Binding.integer(n -> n.swingInterval, (n, v) -> n.swingInterval = v));
        map.put("sneak-interval", Binding.integer(n -> n.sneakInterval, (n, v) -> n.sneakInterval = v));
        map.put("hurt-interval", Binding.integer(n -> n.hurtInterval, (n, v) -> n.hurtInterval = v));
        map.put("actions", Binding.text(n -> n.actions, (n, v) -> n.actions = v));
        map.put("click-type", Binding.text(n -> n.clickType, (n, v) -> n.clickType = v));
        map.put("cooldown", Binding.integer(n -> n.cooldown, (n, v) -> n.cooldown = v));
        map.put("animation-speed", Binding.number(n -> n.animationSpeed, (n, v) -> n.animationSpeed = v));
        map.put("animation", Binding.text(n -> n.animation, (n, v) -> n.animation = v));
        map.put("pose-head", Binding.text(n -> n.poseHead, (n, v) -> n.poseHead = v));
        map.put("pose-body", Binding.text(n -> n.poseBody, (n, v) -> n.poseBody = v));
        map.put("pose-right-arm", Binding.text(n -> n.poseRightArm, (n, v) -> n.poseRightArm = v));
        map.put("pose-left-arm", Binding.text(n -> n.poseLeftArm, (n, v) -> n.poseLeftArm = v));
        map.put("pose-right-leg", Binding.text(n -> n.poseRightLeg, (n, v) -> n.poseRightLeg = v));
        map.put("pose-left-leg", Binding.text(n -> n.poseLeftLeg, (n, v) -> n.poseLeftLeg = v));
        map.put("triggers", Binding.text(n -> n.triggers, (n, v) -> n.triggers = v));
        map.put("look-trigger-range", Binding.number(n -> n.lookTriggerRange, (n, v) -> n.lookTriggerRange = v));
        map.put("approach-range", Binding.number(n -> n.approachRange, (n, v) -> n.approachRange = v));
        map.put("nameplate", Binding.bool(n -> n.nameplate, (n, v) -> n.nameplate = v));
        map.put("nameplate-height", Binding.number(n -> n.nameplateHeight, (n, v) -> n.nameplateHeight = v));
        return map;
    }

    @Override
    public String kind() { return NPC; }

    @Override
    public Map<String, String> values() { return plugin.schema().read(BINDINGS, this); }

    @Override
    public List<String> apply(Map<String, String> values) { return plugin.schema().apply(kind(), BINDINGS, this, values); }

    public Map<EquipmentSlot, ItemStack> equipment() { return equipment; }

    public String entityType() { return entityType; }

    public String skinId() {
        if (!skinValue.isBlank()) {
            try {
                var json = new String(Base64.getDecoder().decode(skinValue.trim()), StandardCharsets.UTF_8);
                var matcher = TEXTURE_HASH.matcher(json);
                if (matcher.find()) return matcher.group(1);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return skinName.isBlank() ? "" : skinName;
    }

    @Override
    protected Built build(Location anchor) {
        var parts = modelParts();
        return parts == null ? buildEntity(anchor) : buildModel(anchor, parts);
    }

    private boolean isModel() {
        return MODEL_TYPE.equalsIgnoreCase(entityType.trim());
    }

    private Map<SkinParts.Part, ProfileProperty> modelParts() {
        if (!isModel()) return null;
        var skin = skinId();
        if (skin.isBlank()) return null;
        var cached = plugin.skinParts().cached(skin);
        if (cached.isEmpty()) {
            requestParts(skin);
            return null;
        }
        return cached.get().keySet().containsAll(REQUIRED_PARTS) ? cached.get() : null;
    }

    private void requestParts(String skin) {
        var service = plugin.skinParts();
        var status = service.status(skin);
        if (status == SkinParts.Status.NO_KEY || status == SkinParts.Status.NO_SKIN || status == SkinParts.Status.READY) return;
        if (status == SkinParts.Status.GENERATING && skin.equals(requestedSkin)) return;
        requestedSkin = skin;
        service.request(skin)
            .thenAccept(result -> rebuildWhenReady(skin, result))
            .exceptionally(e -> {
                plugin.getLogger().log(Level.WARNING, id(), e);
                return null;
            });
    }

    private void rebuildWhenReady(String skin, Optional<Map<SkinParts.Part, ProfileProperty>> result) {
        if (result.isEmpty() || isRemoved() || !isModel() || !skin.equals(skinId())) return;
        plugin.objects().mutate(this, n -> { }).exceptionally(e -> null);
    }

    private Built buildModel(Location anchor, Map<SkinParts.Part, ProfileProperty> parts) {
        var skin = skinId();
        var head = parts.get(SkinParts.Part.HEAD);
        var headProfile = head == null ? profile() : ResolvableProfile.resolvableProfile().addProperty(head).build();
        var built = new SkinnedModel(plugin, new SkinnedModel.Spec(anchor, parts, headProfile, plugin.skinParts().slim(skin), scale, viewRange, glowColor));
        eyeHeight = plugin.getConfig().getDouble(MODEL_EYE_KEY, DEFAULT_MODEL_EYE) * scale;
        template = null;
        handle = null;
        entityId = 0;
        reset(anchor);
        readStaticPose();
        built.glow(glow);
        model = built;
        lastFrame = animate();
        if (keyframes != null) state = keyframes.state(lastFrame);
        applyItems(built);
        setPosition(anchor.getX(), anchor.getY(), anchor.getZ());
        built.pose(poseAngles(), currentYaw);
        var spawn = built.spawn(x(), y(), z());
        return new Built(spawn, List.of(built.destroy()), built.ids());
    }

    private void reset(Location anchor) {
        base = anchor.clone();
        currentYaw = anchor.getYaw();
        sneaking = false;
        looking.clear();
        lookers.clear();
        focus = null;
        runtime.rules(triggers);
        itemOverride = null;
        animationPaused = false;
        animationClock = 0;
        var parsed = preset();
        keyframes = parsed.isEmpty() ? null : parsed;
        state = Keyframes.State.NONE;
        itemsChanged = false;
    }

    private void readStaticPose() {
        Arrays.fill(staticPose, NO_PITCH);
        readAxes(poseHead, Keyframes.HEAD);
        readAxes(poseBody, Keyframes.BODY);
        readAxes(poseRightArm, Keyframes.RIGHT_ARM);
        readAxes(poseLeftArm, Keyframes.LEFT_ARM);
        readAxes(poseRightLeg, Keyframes.RIGHT_LEG);
        readAxes(poseLeftLeg, Keyframes.LEFT_LEG);
    }

    private void readAxes(String text, int offset) {
        if (text == null || text.isBlank()) return;
        var parts = text.split(AXIS_SPLIT, -1);
        for (int axis = 0; axis < Keyframes.AXES && axis < parts.length; axis++) {
            try {
                float value = Float.parseFloat(parts[axis].trim());
                if (Float.isFinite(value)) staticPose[offset + axis] = value;
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private float[] poseAngles() {
        for (int i = 0; i < Keyframes.SIZE; i++) angles[i] = staticPose[i] + sampled[i];
        return angles;
    }

    private void applyItems(SkinnedModel target) {
        var items = shownEquipment();
        target.items(items.get(EquipmentSlot.HAND), items.get(EquipmentSlot.OFF_HAND));
    }

    private Built buildEntity(Location anchor) {
        model = null;
        var type = EntityType.valueOf((isModel() ? EntityType.MANNEQUIN.name() : entityType).toUpperCase(Locale.ROOT));
        var cls = type.getEntityClass();
        if (cls == null) throw new IllegalStateException(entityType);
        var created = anchor.getWorld().createEntity(anchor, cls);
        if (!(created instanceof LivingEntity living)) throw new IllegalStateException(entityType);
        eyeHeight = living.getEyeHeight() * scale;
        configure(living);
        var h = Packets.handle(living);
        template = living;
        handle = h;
        entityId = h.getId();
        reset(anchor);
        lastFrame = animate();
        if (keyframes != null) applyState(living, keyframes.state(lastFrame), false);
        rotationKey = rotationKey(currentYaw);
        setPosition(anchor.getX(), anchor.getY(), anchor.getZ());
        var spawn = spawnPackets();
        Packets.clearDirty(h);
        return new Built(spawn, List.of(Packets.destroy(entityId), Packets.teamRemove(teamName())), new int[]{entityId});
    }

    private void configure(LivingEntity entity) {
        boolean named = !name.isBlank() && !nameplate;
        entity.customName(named ? ColorUtil.colorize(name) : null);
        entity.setCustomNameVisible(nameVisible && named);
        entity.setSilent(true);
        entity.setGlowing(glow);
        if (entity instanceof Ageable ageable) ageable.setAdult();
        basePose = validPose(entity, parsePose());
        entity.setPose(basePose, true);
        if (scale != UNIT_SCALE) {
            var attribute = entity.getAttribute(Attribute.SCALE);
            if (attribute != null) attribute.setBaseValue(scale);
        }
        if (entity instanceof Mannequin mannequin) {
            mannequin.setProfile(profile());
            mannequin.setDescription(description.isBlank() || nameplate ? null : ColorUtil.colorize(description));
            mannequin.setImmovable(true);
        }
    }

    private ResolvableProfile profile() {
        try {
            if (!skinValue.isBlank()) {
                var builder = ResolvableProfile.resolvableProfile()
                    .addProperty(new ProfileProperty(TEXTURES, skinValue, skinSignature.isBlank() ? null : skinSignature));
                if (!skinName.isBlank()) builder.name(skinName);
                return builder.build();
            }
            if (!skinName.isBlank()) return ResolvableProfile.resolvableProfile().name(skinName).build();
        } catch (IllegalArgumentException ignored) {
        }
        return Mannequin.defaultProfile();
    }

    private Map<EquipmentSlot, ItemStack> shownEquipment() {
        Map<EquipmentSlot, ItemStack> merged = new EnumMap<>(EquipmentSlot.class);
        merged.putAll(equipment);
        if (state.item() != null) merged.put(EquipmentSlot.HAND, new ItemStack(state.item()));
        if (state.offItem() != null) merged.put(EquipmentSlot.OFF_HAND, new ItemStack(state.offItem()));
        var held = itemOverride;
        if (held != null) merged.put(EquipmentSlot.HAND, new ItemStack(held));
        return merged;
    }

    private Map<EquipmentSlot, ItemStack> equipmentUpdate() {
        var items = shownEquipment();
        items.computeIfAbsent(EquipmentSlot.HAND, slot -> new ItemStack(Material.AIR));
        items.computeIfAbsent(EquipmentSlot.OFF_HAND, slot -> new ItemStack(Material.AIR));
        return items;
    }

    private Keyframes preset() {
        if (animation.isBlank()) return Keyframes.EMPTY;
        return Keyframes.parse(plugin.getConfig().getString(ANIMATIONS_KEY + animation.trim().toUpperCase(Locale.ROOT)));
    }

    private int animate() {
        var frames = keyframes;
        if (frames != null && animationPaused) return lastFrame;
        bodyOffset = NO_PITCH;
        headOffset = NO_PITCH;
        headPitch = NO_PITCH;
        if (frames == null) {
            Arrays.fill(sampled, NO_PITCH);
            return -1;
        }
        int frame = frames.sample(animationClock * animationSpeed, sampled);
        if (model != null) return frame;
        bodyOffset = sampled[Keyframes.BODY_YAW];
        headOffset = sampled[Keyframes.HEAD_YAW];
        headPitch = sampled[Keyframes.HEAD_PITCH];
        return frame;
    }

    private void gestureModel(SkinnedModel target, int frame) {
        lastFrame = frame;
        var frames = keyframes;
        var next = frames == null ? Keyframes.State.NONE : frames.state(frame);
        var previous = state;
        state = next;
        if (!Objects.equals(next.item(), previous.item()) || !Objects.equals(next.offItem(), previous.offItem())) {
            itemsChanged = true;
            applyItems(target);
        }
        if (frames == null) {
            notifyUse(previous.use(), next.use());
            return;
        }
        var swing = frames.swing(frame);
        if (swing != Keyframes.Hand.NONE) {
            target.swing(swing == Keyframes.Hand.OFF);
            runtime.fire(Triggers.Event.SWING, null, 0);
        }
        notifyUse(previous.use(), next.use());
        runtime.fire(Triggers.Event.FRAME, null, frame);
    }

    private void gesture(LivingEntity entity, int frame, List<Packet<?>> out) {
        lastFrame = frame;
        var frames = keyframes;
        var next = frames == null ? Keyframes.State.NONE : frames.state(frame);
        if (applyState(entity, next, true)) {
            itemsChanged = true;
            out.add(Packets.equipment(entityId, equipmentUpdate()));
        }
        if (frames == null) return;
        var swing = frames.swing(frame);
        if (swing != Keyframes.Hand.NONE) {
            out.add(Packets.swing(handle, swing == Keyframes.Hand.OFF));
            runtime.fire(Triggers.Event.SWING, null, 0);
        }
        runtime.fire(Triggers.Event.FRAME, null, frame);
    }

    private boolean applyState(LivingEntity entity, Keyframes.State next, boolean notify) {
        var previous = state;
        if (next.equals(previous)) return false;
        state = next;
        entity.setPose(currentPose(entity), true);
        Packets.useItem(handle, next.use() != Keyframes.Hand.NONE, next.use() == Keyframes.Hand.OFF);
        if (notify) notifyUse(previous.use(), next.use());
        return !Objects.equals(next.item(), previous.item()) || !Objects.equals(next.offItem(), previous.offItem());
    }

    private Pose currentPose(LivingEntity entity) {
        if (sneaking) return validPose(entity, Pose.SNEAKING);
        var gesture = state.pose();
        return gesture == null ? basePose : validPose(entity, gesture);
    }

    private float bodyYaw(float yaw) { return yaw + bodyOffset; }

    private float headYaw(float yaw) { return yaw + bodyOffset + headOffset; }

    private long rotationKey(float yaw) {
        return (long) Packets.angleKey(bodyYaw(yaw), headPitch) << Integer.SIZE | Packets.angleKey(headYaw(yaw), NO_PITCH);
    }

    private Pose parsePose() {
        try {
            return Pose.valueOf(pose.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Pose.STANDING;
        }
    }

    private static Pose validPose(LivingEntity entity, Pose wanted) {
        return entity instanceof Mannequin && !Mannequin.validPoses().contains(wanted) ? Pose.STANDING : wanted;
    }

    private String teamName() { return TEAM_PREFIX + id(); }

    private List<Packet<?>> spawnPackets() {
        var h = handle;
        var yaw = currentYaw;
        List<Packet<?>> list = new ArrayList<>();
        list.add(Packets.spawn(h, x(), y(), z(), bodyYaw(yaw), headPitch));
        list.add(Packets.fullData(h));
        list.add(Packets.head(h, headYaw(yaw)));
        var items = shownEquipment();
        if (!items.isEmpty()) list.add(Packets.equipment(entityId, items));
        if (scale != UNIT_SCALE) list.add(Packets.attributes(h));
        list.add(Packets.teamCreate(teamName(), template.getUniqueId().toString(), glowColor));
        return list;
    }

    @Override
    protected void tick(int interval) {
        ticks += interval;
        if (!animationPaused) animationClock += interval;
        var shown = model;
        if (shown != null) {
            tickModel(shown, interval);
            return;
        }
        var entity = template;
        var h = handle;
        if (entity == null || h == null) return;
        List<Packet<?>> everyone = new ArrayList<>();
        List<Packet<?>> rotation = new ArrayList<>();
        int frame = animate();
        if (frame != lastFrame) gesture(entity, frame, everyone);
        double seconds = ticks / (double) TICKS_PER_SECOND;
        float yaw = currentYaw;
        var point = pathPoint(seconds);
        if (point != null) {
            yaw = (float) point[3];
            setPosition(point[0], point[1], point[2]);
            everyone.add(Packets.teleport(entityId, point[0], point[1], point[2], bodyYaw(yaw), headPitch));
            everyone.add(Packets.head(h, headYaw(yaw)));
            rotationKey = rotationKey(yaw);
        } else {
            if (spinSpeed != 0) yaw = base.getYaw() + (float) (spinSpeed * seconds);
            long key = rotationKey(yaw);
            if (key != rotationKey) {
                rotation.add(Packets.rotation(entityId, bodyYaw(yaw), headPitch));
                rotation.add(Packets.head(h, headYaw(yaw)));
                rotationKey = key;
            }
        }
        currentYaw = yaw;
        if (crossed(sneakInterval, interval)) {
            sneaking = !sneaking;
            entity.setPose(currentPose(entity), true);
        }
        boolean swung = crossed(swingInterval, interval);
        if (swung) everyone.add(Packets.swing(h, false));
        if (crossed(hurtInterval, interval)) everyone.add(Packets.hurt(entityId, yaw));
        var data = Packets.dirtyData(h, false);
        if (data != null) everyone.add(data);
        broadcast(everyone);
        broadcast(rotation, looking.keySet());
        finishTick(swung, interval);
    }

    private void finishTick(boolean swung, int interval) {
        particles(x(), y() + PARTICLE_HEIGHT, z(), interval);
        if (swung) runtime.fire(Triggers.Event.SWING, null, 0);
        runtime.tick(interval, holdingTest);
    }

    private void tickModel(SkinnedModel shown, int interval) {
        List<Packet<?>> out = new ArrayList<>();
        int frame = animate();
        if (frame != lastFrame) gestureModel(shown, frame);
        double seconds = ticks / (double) TICKS_PER_SECOND;
        float yaw = currentYaw;
        double moved = 0;
        var point = pathPoint(seconds);
        if (point != null) {
            moved = Math.hypot(point[0] - x(), point[2] - z());
            yaw = (float) point[3];
            if (moved > 0 || point[1] != y()) {
                setPosition(point[0], point[1], point[2]);
                out.addAll(shown.teleport(point[0], point[1], point[2]));
            }
        } else if (spinSpeed != 0) {
            yaw = base.getYaw() + (float) (spinSpeed * seconds);
        }
        currentYaw = yaw;
        boolean swung = crossed(swingInterval, interval);
        if (swung) shown.swing(false);
        out.addAll(shown.tick(poseAngles(), yaw, moved, lookTarget(), interval));
        broadcast(out);
        finishTick(swung, interval);
    }

    private double[] lookTarget() {
        double hx = x();
        double hy = y() + eyeHeight;
        double hz = z();
        var forced = focus;
        if (forced != null && System.currentTimeMillis() < forced.until()) {
            return new double[]{forced.x() - hx, forced.y() - hy, forced.z() - hz};
        }
        if (!lookAt) return null;
        double nearest = lookRange * lookRange;
        double[] pick = null;
        for (var eye : lookers.values()) {
            double dx = eye[0] - hx;
            double dy = eye[1] - hy;
            double dz = eye[2] - hz;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance > nearest) continue;
            nearest = distance;
            pick = new double[]{dx, dy, dz};
        }
        return pick;
    }

    private Material heldItem() {
        var override = itemOverride;
        if (override != null) return override;
        var gesture = state.item();
        if (gesture != null) return gesture;
        var saved = equipment.get(EquipmentSlot.HAND);
        return saved == null ? Material.AIR : saved.getType();
    }

    private void notifyUse(Keyframes.Hand before, Keyframes.Hand after) {
        boolean was = before != Keyframes.Hand.NONE;
        boolean is = after != Keyframes.Hand.NONE;
        if (!was && is) runtime.fire(Triggers.Event.USE, null, 0);
        else if (was && !is) runtime.fire(Triggers.Event.USESTOP, null, 0);
    }

    @Override
    public TriggerRuntime runtime() { return runtime; }

    @Override
    public double lookTriggerRange() { return lookTriggerRange; }

    @Override
    public double approachTriggerRange() { return approachRange; }

    @Override
    public float facingYaw() { return facing(); }

    @Override
    public Vector anchorPoint(String anchor) {
        if (anchor == null) return null;
        double eye = eyeHeight;
        return switch (anchor.toLowerCase(Locale.ROOT)) {
            case Triggers.ANCHOR_HEAD -> new Vector(x(), y() + eye, z());
            case ANCHOR_FEET -> new Vector(x(), y() + FEET_LIFT * scale, z());
            case ANCHOR_BOTTOM -> new Vector(x(), y(), z());
            case Triggers.ANCHOR_BODY -> new Vector(x(), y() + eye * BODY_FACTOR, z());
            case Triggers.ANCHOR_CENTER -> new Vector(x(), y() + eye * CENTRE_FACTOR, z());
            case ANCHOR_TOP -> new Vector(x(), y() + eye + TOP_LIFT * scale, z());
            case ANCHOR_ABOVE -> new Vector(x(), y() + eye + nameplateHeight + ABOVE_LIFT * scale, z());
            case ANCHOR_HAND -> side(1);
            case ANCHOR_OFFHAND -> side(-1);
            default -> null;
        };
    }

    private Vector side(int sign) {
        double yaw = Math.toRadians(facing());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double offset = HAND_SIDE * scale * sign;
        double forward = HAND_FORWARD * scale;
        return new Vector(x() + rightX * offset + forwardX * forward, y() + eyeHeight * HAND_FACTOR, z() + rightZ * offset + forwardZ * forward);
    }

    @Override
    public boolean hostAction(String verb, List<String> args, Player player) {
        var arg = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
        return switch (verb) {
            case VERB_SWING -> swingVerb(arg);
            case VERB_GLOW -> glowVerb(arg);
            case VERB_ANIMATE -> animateVerb(arg);
            case VERB_ITEM -> itemVerb(arg);
            case VERB_LOOK -> lookVerb(player);
            case VERB_HURT -> {
                hurt();
                yield true;
            }
            case VERB_ANIMATION -> swapAnimation(arg);
            default -> false;
        };
    }

    private boolean swingVerb(String arg) {
        if (arg.isEmpty() || arg.equals(ARG_MAIN)) swing(false);
        else if (arg.equals(ARG_OFF)) swing(true);
        else return false;
        return true;
    }

    private boolean glowVerb(String arg) {
        var mode = switch (arg) {
            case ARG_ON -> Triggers.Toggle.ON;
            case ARG_OFF -> Triggers.Toggle.OFF;
            case ARG_TOGGLE, "" -> Triggers.Toggle.TOGGLE;
            default -> null;
        };
        if (mode == null) return false;
        glow(mode);
        return true;
    }

    private boolean animateVerb(String arg) {
        var mode = switch (arg) {
            case ARG_ON -> Triggers.Playback.ON;
            case ARG_OFF -> Triggers.Playback.OFF;
            case ARG_RESTART -> Triggers.Playback.RESTART;
            default -> null;
        };
        if (mode == null) return false;
        playback(mode);
        return true;
    }

    private boolean itemVerb(String arg) {
        if (arg.isEmpty()) return false;
        if (arg.equals(ARG_NONE) || arg.equals(ARG_AIR)) {
            overrideItem(null);
            return true;
        }
        var material = Material.matchMaterial(arg);
        if (material == null || !material.isItem() || material.isAir()) return false;
        overrideItem(material);
        return true;
    }

    private boolean lookVerb(Player player) {
        if (player == null) return false;
        var eye = runtime.eye(player.getUniqueId());
        if (eye == null) return false;
        faceToward(player, eye);
        return true;
    }

    private boolean swapAnimation(String preset) {
        if (preset.isBlank()) return false;
        var parsed = Keyframes.parse(plugin.getConfig().getString(ANIMATIONS_KEY + preset.trim().toUpperCase(Locale.ROOT)));
        if (parsed.isEmpty() && !ANIMATION_NONE.equalsIgnoreCase(preset)) return false;
        keyframes = parsed.isEmpty() ? null : parsed;
        animationClock = 0;
        animationPaused = false;
        lastFrame = RESET_FRAME;
        return true;
    }

    public double eyeHeight() { return eyeHeight; }

    public double scale() { return scale; }

    public double nameplateHeight() { return nameplateHeight; }

    public boolean nameplate() { return nameplate; }

    public float facing() { return bodyYaw(currentYaw); }

    public void swing(boolean offHand) {
        var shown = model;
        if (shown != null) {
            shown.swing(offHand);
            return;
        }
        var h = handle;
        if (h != null) broadcast(List.of(Packets.swing(h, offHand)));
    }

    public void hurt() {
        if (handle != null) broadcast(List.of(Packets.hurt(entityId, currentYaw)));
    }

    public void glow(Triggers.Toggle mode) {
        var shown = model;
        if (shown != null) {
            shown.glow(switch (mode) {
                case ON -> true;
                case OFF -> false;
                case TOGGLE -> !shown.glowing();
            });
            return;
        }
        var entity = template;
        if (entity == null) return;
        entity.setGlowing(switch (mode) {
            case ON -> true;
            case OFF -> false;
            case TOGGLE -> !entity.isGlowing();
        });
    }

    public void playback(Triggers.Playback mode) {
        switch (mode) {
            case ON -> animationPaused = false;
            case OFF -> animationPaused = true;
            case RESTART -> {
                animationClock = 0;
                animationPaused = false;
            }
        }
    }

    public void overrideItem(Material material) {
        var shown = model;
        if (shown != null) {
            itemOverride = material;
            itemsChanged = true;
            applyItems(shown);
            return;
        }
        if (handle == null) return;
        itemOverride = material;
        itemsChanged = true;
        broadcast(List.of(Packets.equipment(entityId, equipmentUpdate())));
    }

    public void faceToward(Player player, double[] eye) {
        if (model != null) {
            long hold = plugin.getConfig().getLong(LOOK_HOLD_KEY, DEFAULT_LOOK_HOLD);
            focus = new Focus(eye[0], eye[1], eye[2], System.currentTimeMillis() + hold * MILLIS_PER_TICK);
            return;
        }
        var h = handle;
        if (h == null) return;
        var aim = aim(eye);
        Packets.send(player, Packets.bundle(List.of(Packets.rotation(entityId, aim[0], aim[1]), Packets.head(h, aim[0]))));
        var uuid = player.getUniqueId();
        long hold = plugin.getConfig().getLong(LOOK_HOLD_KEY, DEFAULT_LOOK_HOLD);
        plugin.scheduler().runAtEntityDelayed(player, () -> {
            if (!looking.containsKey(uuid) && isViewer(uuid)) restoreFacing(player);
        }, Math.max(1L, hold));
    }

    private float[] aim(double[] eye) {
        double dx = eye[0] - x();
        double dy = eye[1] - (y() + eyeHeight);
        double dz = eye[2] - z();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return new float[]{yaw, pitch};
    }

    private void restoreFacing(Player viewer) {
        var h = handle;
        if (h == null) return;
        Packets.send(viewer, Packets.rotation(entityId, bodyYaw(currentYaw), headPitch));
        Packets.send(viewer, Packets.head(h, headYaw(currentYaw)));
    }

    private boolean crossed(int period, int interval) {
        return period > 0 && ticks / period != (ticks - interval) / period;
    }

    public void look(Player viewer, Location viewerEye) {
        if (model != null) {
            if (lookAt) lookers.put(viewer.getUniqueId(), new double[]{viewerEye.getX(), viewerEye.getY(), viewerEye.getZ()});
            else lookers.remove(viewer.getUniqueId());
            return;
        }
        var h = handle;
        if (h == null) return;
        double dx = viewerEye.getX() - x();
        double dy = viewerEye.getY() - (y() + eyeHeight);
        double dz = viewerEye.getZ() - z();
        double horizontal = Math.hypot(dx, dz);
        double range = lookRange;
        if (!lookAt || dx * dx + dy * dy + dz * dz > range * range) {
            release(viewer, h);
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        Integer key = Packets.angleKey(yaw, pitch);
        if (key.equals(looking.put(viewer.getUniqueId(), key))) return;
        Packets.send(viewer, Packets.bundle(List.of(Packets.rotation(entityId, yaw, pitch), Packets.head(h, yaw))));
    }

    @Override
    protected List<Packet<?>> catchUp() {
        var shown = model;
        if (shown != null) return shown.catchUp(x(), y(), z());
        var h = handle;
        if (h == null) return List.of();
        List<Packet<?>> list = new ArrayList<>();
        list.add(Packets.teleport(entityId, x(), y(), z(), bodyYaw(currentYaw), headPitch));
        list.add(Packets.head(h, headYaw(currentYaw)));
        var data = Packets.fullData(h);
        if (data != null) list.add(data);
        if (itemsChanged) list.add(Packets.equipment(entityId, equipmentUpdate()));
        return list;
    }

    private void release(Player viewer, Entity h) {
        if (looking.remove(viewer.getUniqueId()) == null) return;
        Packets.send(viewer, Packets.rotation(entityId, bodyYaw(currentYaw), headPitch));
        Packets.send(viewer, Packets.head(h, headYaw(currentYaw)));
    }

    @Override
    protected void onHide(UUID viewer) {
        looking.remove(viewer);
        lookers.remove(viewer);
        runtime.forget(viewer);
    }

    public void click(Player player, boolean attack) {
        runActions(player, attack);
        runtime.click(player, attack);
    }

    private void runActions(Player player, boolean attack) {
        if (actions.isBlank() || !accepts(attack)) return;
        var uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        cooldowns.values().removeIf(until -> until <= now);
        if (cooldowns.containsKey(uuid)) return;
        if (cooldown > 0) cooldowns.put(uuid, now + cooldown * MILLIS_PER_TICK);
        plugin.actions().run(player, actions, Map.of("player", player.getName(), "npc", id(), "uuid", uuid.toString()));
    }

    private boolean accepts(boolean attack) {
        ClickType type;
        try {
            type = ClickType.valueOf(clickType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = ClickType.ANY;
        }
        return switch (type) {
            case ANY -> true;
            case RIGHT -> !attack;
            case LEFT -> attack;
        };
    }

    @Override
    protected void writeExtra(Map<String, Object> out) {
        Map<String, String> encoded = new LinkedHashMap<>();
        equipment.forEach((slot, item) -> {
            var data = ItemCodec.encode(item);
            if (data != null) encoded.put(slot.name(), data);
        });
        out.put(EXTRA_EQUIPMENT, encoded);
    }

    @Override
    protected void readExtra(ConfigurationSection section) {
        equipment.clear();
        var items = section.getConfigurationSection(EXTRA_EQUIPMENT);
        if (items == null) return;
        for (var key : items.getKeys(false)) {
            try {
                var slot = EquipmentSlot.valueOf(key);
                ItemCodec.decode(items.getString(key)).ifPresent(item -> equipment.put(slot, item));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
