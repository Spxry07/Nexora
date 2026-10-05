package net.spxry.nexora.object;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.spxry.nexora.npc.Effects;
import net.spxry.nexora.npc.SkinPalette;
import net.spxry.nexora.npc.Triggers;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.edit.Binding;
import net.spxry.nexora.nms.Packets;
import net.spxry.nexora.util.ColorUtil;
import net.spxry.nexora.util.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class Npc extends NexoraObject {
    private enum ClickType { ANY, RIGHT, LEFT }

    private record CooldownKey(int rule, UUID player) {}

    private static final UUID NO_PLAYER = new UUID(0L, 0L);
    private static final String LOOK_ANGLE_KEY = "npc.triggers.look-angle";
    private static final String DEFAULT_COOLDOWN_KEY = "npc.triggers.default-cooldown";
    private static final String LOOK_HOLD_KEY = "npc.triggers.look-hold-ticks";
    private static final double DEFAULT_LOOK_ANGLE = 10.0;
    private static final int DEFAULT_COOLDOWN = 40;
    private static final long DEFAULT_LOOK_HOLD = 40L;
    private static final double LOOK_AWAY_FACTOR = 1.5;
    private static final double RANGE_HYSTERESIS = 0.5;
    private static final double BODY_CENTRE = 0.5;
    private static final double MIN_LENGTH = 1.0E-6;
    private static final int PRUNE_PERIOD = 200;

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
    private static final String PALETTE_ENABLED_KEY = "npc.skin-palette.enabled";

    public static final Map<String, Binding<Npc>> BINDINGS = bindings();

    private final Map<EquipmentSlot, ItemStack> equipment = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> looking = new ConcurrentHashMap<>();
    private final Map<CooldownKey, Long> ruleCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> gazing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> near = ConcurrentHashMap.newKeySet();
    private final Effects effects;
    private volatile Map<Triggers.Event, List<Triggers.Rule>> rules = Map.of();
    private volatile double lookCos;
    private volatile double lookAwayCos;
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
    private boolean animationEnabled;
    private double animationSpeed = DEFAULT_SPEED;
    private String animation = "";
    private boolean small;

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
    private volatile ItemStack skinHead;
    private long rotationKey;
    private int lastFrame = -1;
    private boolean itemsChanged;
    private Keyframes.State state = Keyframes.State.NONE;
    private volatile Map<EquipmentSlot, ItemStack> tint = Map.of();
    private volatile String tintId = "";
    private final float[] limbs = new float[Keyframes.SIZE];
    private final float[] appliedPose = new float[Keyframes.SIZE];

    public Npc(Nexora plugin, String id, Location anchor) {
        super(plugin, id, anchor);
        this.entityType = plugin.getConfig().getString(DEFAULT_TYPE_KEY, "");
        this.base = anchor.clone();
        this.currentYaw = anchor.getYaw();
        this.effects = new Effects(plugin, this);
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
        map.put("animation-enabled", Binding.bool(n -> n.animationEnabled, (n, v) -> n.animationEnabled = v));
        map.put("animation-speed", Binding.number(n -> n.animationSpeed, (n, v) -> n.animationSpeed = v));
        map.put("animation", Binding.text(n -> n.animation, (n, v) -> n.animation = v));
        map.put("small", Binding.bool(n -> n.small, (n, v) -> n.small = v));
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
        var type = EntityType.valueOf(entityType.toUpperCase(Locale.ROOT));
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
        base = anchor.clone();
        currentYaw = anchor.getYaw();
        sneaking = false;
        looking.clear();
        ruleCooldowns.clear();
        rules = Triggers.parse(plugin, triggers);
        double angle = plugin.getConfig().getDouble(LOOK_ANGLE_KEY, DEFAULT_LOOK_ANGLE);
        lookCos = Math.cos(Math.toRadians(angle));
        lookAwayCos = Math.cos(Math.toRadians(angle * LOOK_AWAY_FACTOR));
        itemOverride = null;
        animationPaused = false;
        animationClock = 0;
        var parsed = animationEnabled ? Keyframes.parse(animation) : Keyframes.EMPTY;
        keyframes = parsed.isEmpty() ? null : parsed;
        skinHead = living instanceof ArmorStand ? headItem() : null;
        tint(living);
        Arrays.fill(appliedPose, Float.NaN);
        state = Keyframes.State.NONE;
        itemsChanged = false;
        lastFrame = animate(living);
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
        if (entity instanceof ArmorStand stand) {
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setSmall(small);
            stand.setGravity(false);
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

    private ItemStack headItem() {
        if (skinValue.isBlank() && skinName.isBlank()) return null;
        var item = new ItemStack(Material.PLAYER_HEAD);
        item.setData(DataComponentTypes.PROFILE, profile());
        return item;
    }

    private boolean emptySlot(EquipmentSlot slot) {
        var current = equipment.get(slot);
        return current == null || current.getType().isAir();
    }

    private Map<EquipmentSlot, ItemStack> shownEquipment() {
        Map<EquipmentSlot, ItemStack> merged = new EnumMap<>(EquipmentSlot.class);
        merged.putAll(equipment);
        var head = skinHead;
        if (head != null && emptySlot(EquipmentSlot.HEAD)) merged.put(EquipmentSlot.HEAD, head);
        for (var entry : tint.entrySet()) if (emptySlot(entry.getKey())) merged.put(entry.getKey(), entry.getValue());
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

    private void tint(LivingEntity living) {
        tint = Map.of();
        tintId = "";
        if (!(living instanceof ArmorStand) || skinHead == null || !plugin.getConfig().getBoolean(PALETTE_ENABLED_KEY, true)) return;
        var skin = skinId();
        if (skin.isBlank()) return;
        var cached = plugin.skins().cached(skin);
        if (cached.isPresent()) {
            tint = dye(cached.get());
            tintId = skin;
            return;
        }
        plugin.skins().request(skin)
            .thenAccept(found -> retint(skin, found.isPresent()))
            .exceptionally(e -> {
                plugin.getLogger().log(Level.WARNING, id(), e);
                return null;
            });
    }

    private void retint(String skin, boolean found) {
        if (!found || isRemoved() || skin.equals(tintId) || !skin.equals(skinId())) return;
        plugin.objects().mutate(this, n -> {}).exceptionally(e -> {
            if (!isRemoved()) plugin.getLogger().log(Level.WARNING, id(), e);
            return null;
        });
    }

    private static Map<EquipmentSlot, ItemStack> dye(SkinPalette.Palette palette) {
        Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        items.put(EquipmentSlot.CHEST, dyed(Material.LEATHER_CHESTPLATE, palette.torso()));
        items.put(EquipmentSlot.LEGS, dyed(Material.LEATHER_LEGGINGS, palette.legs()));
        items.put(EquipmentSlot.FEET, dyed(Material.LEATHER_BOOTS, palette.feet()));
        return items;
    }

    private static ItemStack dyed(Material material, Color color) {
        var item = new ItemStack(material);
        item.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));
        return item;
    }

    private int animate(LivingEntity entity) {
        var frames = keyframes;
        if (frames != null && animationPaused) return lastFrame;
        bodyOffset = NO_PITCH;
        headOffset = NO_PITCH;
        headPitch = NO_PITCH;
        if (frames == null) return -1;
        int frame = frames.sample(animationClock * animationSpeed, limbs);
        if (entity instanceof ArmorStand stand) {
            applyPose(stand);
            return frame;
        }
        bodyOffset = limbs[Keyframes.at(Keyframes.BODY, Keyframes.YAW)];
        headOffset = limbs[Keyframes.at(Keyframes.HEAD, Keyframes.YAW)];
        headPitch = limbs[Keyframes.at(Keyframes.HEAD, Keyframes.PITCH)];
        return frame;
    }

    private void gesture(LivingEntity entity, int frame, List<Packet<?>> out) {
        lastFrame = frame;
        var frames = keyframes;
        if (frames == null) return;
        if (applyState(entity, frames.state(frame), true)) {
            itemsChanged = true;
            out.add(Packets.equipment(entityId, equipmentUpdate()));
        }
        var swing = frames.swing(frame);
        if (swing != Keyframes.Hand.NONE) {
            out.add(Packets.swing(handle, swing == Keyframes.Hand.OFF));
            fire(Triggers.Event.SWING, null, true, 0);
        }
        fire(Triggers.Event.FRAME, null, true, frame);
    }

    private boolean applyState(LivingEntity entity, Keyframes.State next, boolean notify) {
        var previous = state;
        if (next.equals(previous)) return false;
        state = next;
        entity.setPose(currentPose(entity), true);
        if (!(entity instanceof ArmorStand)) Packets.useItem(handle, next.use() != Keyframes.Hand.NONE, next.use() == Keyframes.Hand.OFF);
        if (notify) notifyUse(previous.use(), next.use());
        return !Objects.equals(next.item(), previous.item()) || !Objects.equals(next.offItem(), previous.offItem());
    }

    private Pose currentPose(LivingEntity entity) {
        if (sneaking) return validPose(entity, Pose.SNEAKING);
        var gesture = state.pose();
        return gesture == null ? basePose : validPose(entity, gesture);
    }

    private void applyPose(ArmorStand stand) {
        for (int limb = 0; limb < Keyframes.LIMBS; limb++) {
            int at = Keyframes.at(limb, Keyframes.PITCH);
            if (limbs[at] == appliedPose[at] && limbs[at + 1] == appliedPose[at + 1] && limbs[at + 2] == appliedPose[at + 2]) continue;
            System.arraycopy(limbs, at, appliedPose, at, Keyframes.AXES);
            var angle = new EulerAngle(Math.toRadians(limbs[at]), Math.toRadians(limbs[at + 1]), Math.toRadians(limbs[at + 2]));
            switch (limb) {
                case Keyframes.HEAD -> stand.setHeadPose(angle);
                case Keyframes.BODY -> stand.setBodyPose(angle);
                case Keyframes.LEFT_ARM -> stand.setLeftArmPose(angle);
                case Keyframes.RIGHT_ARM -> stand.setRightArmPose(angle);
                case Keyframes.LEFT_LEG -> stand.setLeftLegPose(angle);
                default -> stand.setRightLegPose(angle);
            }
        }
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
        var entity = template;
        var h = handle;
        if (entity == null || h == null) return;
        List<Packet<?>> everyone = new ArrayList<>();
        List<Packet<?>> rotation = new ArrayList<>();
        int frame = animate(entity);
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
        particles(x(), y() + PARTICLE_HEIGHT, z(), interval);
        if (swung) fire(Triggers.Event.SWING, null, true, 0);
        periodic(Triggers.Event.INTERVAL, interval);
        periodic(Triggers.Event.HOLDING, interval);
        if (crossed(PRUNE_PERIOD, interval)) pruneCooldowns();
    }

    private void periodic(Triggers.Event event, int interval) {
        var list = rules.get(event);
        if (list == null) return;
        for (var rule : list) {
            if (!crossed(rule.every(), interval)) continue;
            if (event == Triggers.Event.HOLDING && !holds(rule.material())) continue;
            fire(rule, null, true);
        }
    }

    private boolean holds(Material wanted) {
        return heldItem() == wanted;
    }

    private Material heldItem() {
        var override = itemOverride;
        if (override != null) return override;
        var gesture = state.item();
        if (gesture != null) return gesture;
        var saved = equipment.get(EquipmentSlot.HAND);
        return saved == null ? Material.AIR : saved.getType();
    }

    private void pruneCooldowns() {
        long now = System.currentTimeMillis();
        ruleCooldowns.values().removeIf(until -> until <= now);
    }

    private void fire(Triggers.Event event, Player player, boolean region, int value) {
        var list = rules.get(event);
        if (list == null) return;
        for (var rule : list) if (rule.accepts(value)) fire(rule, player, region);
    }

    private void fire(Triggers.Rule rule, Player player, boolean region) {
        if (!ready(rule, player)) return;
        effects.run(rule.actions(), player, region);
    }

    private boolean ready(Triggers.Rule rule, Player player) {
        int cooldownTicks = rule.cooldown() != Triggers.UNSET_COOLDOWN ? rule.cooldown()
            : rule.event().playerScoped() ? plugin.getConfig().getInt(DEFAULT_COOLDOWN_KEY, DEFAULT_COOLDOWN) : 0;
        if (cooldownTicks <= 0) return true;
        var key = new CooldownKey(rule.index(), player == null ? NO_PLAYER : player.getUniqueId());
        long now = System.currentTimeMillis();
        var until = ruleCooldowns.get(key);
        if (until != null && until > now) return false;
        ruleCooldowns.put(key, now + cooldownTicks * MILLIS_PER_TICK);
        return true;
    }

    private void notifyUse(Keyframes.Hand before, Keyframes.Hand after) {
        boolean was = before != Keyframes.Hand.NONE;
        boolean is = after != Keyframes.Hand.NONE;
        if (!was && is) fire(Triggers.Event.USE, null, true, 0);
        else if (was && !is) fire(Triggers.Event.USESTOP, null, true, 0);
    }

    public void shown(Player player) {
        fire(Triggers.Event.SPAWN, player, false, 0);
    }

    public void departed(Player player) {
        var uuid = player.getUniqueId();
        if (near.remove(uuid)) fire(Triggers.Event.LEAVE, player, false, 0);
        if (gazing.remove(uuid)) fire(Triggers.Event.LOOKAWAY, player, false, 0);
    }

    public void observe(Player player, Location eye, Vector direction) {
        var table = rules;
        if (table.isEmpty()) return;
        var uuid = player.getUniqueId();
        double dx = x() - eye.getX();
        double dz = z() - eye.getZ();
        double dy = y() + eyeHeight * BODY_CENTRE - eye.getY();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (table.containsKey(Triggers.Event.APPROACH) || table.containsKey(Triggers.Event.LEAVE)) {
            boolean now = within(distanceSquared, approachRange, near.contains(uuid));
            if (now && near.add(uuid)) fire(Triggers.Event.APPROACH, player, false, 0);
            else if (!now && near.remove(uuid)) fire(Triggers.Event.LEAVE, player, false, 0);
        }
        if (table.containsKey(Triggers.Event.LOOK) || table.containsKey(Triggers.Event.LOOKAWAY)) {
            boolean was = gazing.contains(uuid);
            boolean now = within(distanceSquared, lookTriggerRange, was) && aimed(eye, direction, was ? lookAwayCos : lookCos);
            if (now && gazing.add(uuid)) fire(Triggers.Event.LOOK, player, false, 0);
            else if (!now && gazing.remove(uuid)) fire(Triggers.Event.LOOKAWAY, player, false, 0);
        }
    }

    private static boolean within(double distanceSquared, double range, boolean already) {
        double limit = already ? range + RANGE_HYSTERESIS : range;
        return distanceSquared <= limit * limit;
    }

    private boolean aimed(Location eye, Vector direction, double threshold) {
        return aimedAt(eye, direction, eyeHeight, threshold) || aimedAt(eye, direction, eyeHeight * BODY_CENTRE, threshold);
    }

    private boolean aimedAt(Location eye, Vector direction, double height, double threshold) {
        double dx = x() - eye.getX();
        double dy = y() + height - eye.getY();
        double dz = z() - eye.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < MIN_LENGTH) return true;
        return (direction.getX() * dx + direction.getY() * dy + direction.getZ() * dz) / length >= threshold;
    }

    public double eyeHeight() { return eyeHeight; }

    public double scale() { return scale; }

    public double nameplateHeight() { return nameplateHeight; }

    public boolean nameplate() { return nameplate; }

    public float facing() { return bodyYaw(currentYaw); }

    public List<Player> viewerList() {
        List<Player> players = new ArrayList<>();
        for (var uuid : viewers()) {
            var player = Bukkit.getPlayer(uuid);
            if (player != null) players.add(player);
        }
        return players;
    }

    public void swing(boolean offHand) {
        var h = handle;
        if (h != null) broadcast(List.of(Packets.swing(h, offHand)));
    }

    public void hurt() {
        if (handle != null) broadcast(List.of(Packets.hurt(entityId, currentYaw)));
    }

    public void glow(Triggers.Toggle mode) {
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
        if (handle == null) return;
        itemOverride = material;
        itemsChanged = true;
        broadcast(List.of(Packets.equipment(entityId, equipmentUpdate())));
    }

    public void faceToward(Player player) {
        var h = handle;
        if (h == null) return;
        var aim = aim(player.getEyeLocation());
        Packets.send(player, Packets.bundle(List.of(Packets.rotation(entityId, aim[0], aim[1]), Packets.head(h, aim[0]))));
        var uuid = player.getUniqueId();
        long hold = plugin.getConfig().getLong(LOOK_HOLD_KEY, DEFAULT_LOOK_HOLD);
        plugin.scheduler().runAtEntityDelayed(player, () -> {
            if (!looking.containsKey(uuid) && isViewer(uuid)) restoreFacing(player);
        }, Math.max(1L, hold));
    }

    private float[] aim(Location eye) {
        double dx = eye.getX() - x();
        double dy = eye.getY() - (y() + eyeHeight);
        double dz = eye.getZ() - z();
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
        gazing.remove(viewer);
        near.remove(viewer);
    }

    public void click(Player player, boolean attack) {
        runActions(player, attack);
        fire(Triggers.Event.CLICK, player, false, 0);
        fire(attack ? Triggers.Event.LEFTCLICK : Triggers.Event.RIGHTCLICK, player, false, 0);
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
