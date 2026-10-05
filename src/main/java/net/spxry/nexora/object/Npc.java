package net.spxry.nexora.object;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class Npc extends NexoraObject {
    private enum ClickType { ANY, RIGHT, LEFT }

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

    public static final Map<String, Binding<Npc>> BINDINGS = bindings();

    private final Map<EquipmentSlot, ItemStack> equipment = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> looking = new ConcurrentHashMap<>();

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
    private final float[] limbs = new float[Keyframes.SIZE];
    private final float[] appliedPose = new float[Keyframes.SIZE];

    public Npc(Nexora plugin, String id, Location anchor) {
        super(plugin, id, anchor);
        this.entityType = plugin.getConfig().getString(DEFAULT_TYPE_KEY, "");
        this.base = anchor.clone();
        this.currentYaw = anchor.getYaw();
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
        var parsed = animationEnabled ? Keyframes.parse(animation) : Keyframes.EMPTY;
        keyframes = parsed.isEmpty() ? null : parsed;
        skinHead = living instanceof ArmorStand ? headItem() : null;
        Arrays.fill(appliedPose, Float.NaN);
        animate(living);
        rotationKey = rotationKey(currentYaw);
        setPosition(anchor.getX(), anchor.getY(), anchor.getZ());
        var spawn = spawnPackets();
        Packets.clearDirty(h);
        return new Built(spawn, List.of(Packets.destroy(entityId), Packets.teamRemove(teamName())), new int[]{entityId});
    }

    private void configure(LivingEntity entity) {
        boolean named = !name.isBlank();
        if (named) entity.customName(ColorUtil.colorize(name));
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
            mannequin.setDescription(description.isBlank() ? null : ColorUtil.colorize(description));
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

    private Map<EquipmentSlot, ItemStack> visibleEquipment() {
        var head = skinHead;
        var current = equipment.get(EquipmentSlot.HEAD);
        if (head == null || (current != null && !current.getType().isAir())) return Map.copyOf(equipment);
        Map<EquipmentSlot, ItemStack> merged = new EnumMap<>(EquipmentSlot.class);
        merged.putAll(equipment);
        merged.put(EquipmentSlot.HEAD, head);
        return merged;
    }

    private void animate(LivingEntity entity) {
        var frames = keyframes;
        bodyOffset = NO_PITCH;
        headOffset = NO_PITCH;
        headPitch = NO_PITCH;
        if (frames == null) return;
        frames.sample(ticks * animationSpeed, limbs);
        if (entity instanceof ArmorStand stand) {
            applyPose(stand);
            return;
        }
        bodyOffset = limbs[Keyframes.at(Keyframes.BODY, Keyframes.YAW)];
        headOffset = limbs[Keyframes.at(Keyframes.HEAD, Keyframes.YAW)];
        headPitch = limbs[Keyframes.at(Keyframes.HEAD, Keyframes.PITCH)];
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
        var items = visibleEquipment();
        if (!items.isEmpty()) list.add(Packets.equipment(entityId, items));
        if (scale != UNIT_SCALE) list.add(Packets.attributes(h));
        list.add(Packets.teamCreate(teamName(), template.getUniqueId().toString(), glowColor));
        return list;
    }

    @Override
    protected void tick(int interval) {
        ticks += interval;
        var entity = template;
        var h = handle;
        if (entity == null || h == null) return;
        List<Packet<?>> everyone = new ArrayList<>();
        List<Packet<?>> rotation = new ArrayList<>();
        animate(entity);
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
            entity.setPose(sneaking ? validPose(entity, Pose.SNEAKING) : basePose, true);
        }
        if (crossed(swingInterval, interval)) everyone.add(Packets.swing(h, false));
        if (crossed(hurtInterval, interval)) everyone.add(Packets.hurt(entityId, yaw));
        var data = Packets.dirtyData(h, false);
        if (data != null) everyone.add(data);
        broadcast(everyone);
        broadcast(rotation, looking.keySet());
        particles(x(), y() + PARTICLE_HEIGHT, z(), interval);
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
    }

    public void click(Player player, boolean attack) {
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
