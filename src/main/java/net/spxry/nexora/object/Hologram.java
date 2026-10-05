package net.spxry.nexora.object;

import net.minecraft.network.protocol.Packet;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.edit.Binding;
import net.spxry.nexora.nms.Packets;
import net.spxry.nexora.util.ColorUtil;
import net.spxry.nexora.util.ItemCodec;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Hologram extends NexoraObject {
    public static final Map<String, Binding<Hologram>> BINDINGS = createBindings();

    private static final String LINES_KEY = "lines";
    private static final String PROPS_KEY = "props";
    private static final String ITEM_KEY = "item";
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double FULL_TURN = 360.0;
    private static final int MAX_TELEPORT_DURATION = 59;
    private static final float BLOCK_CENTER = -0.5f;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final String LAYOUT_STACK = "STACK";
    private static final String LAYOUT_RING = "RING";
    private static final String LAYOUT_HELIX = "HELIX";
    private static final String LAYOUT_ROW = "ROW";
    private static final String LAYOUT_WHEEL = "WHEEL";
    private static final String LAYOUT_SPHERE = "SPHERE";
    private static final String LAYOUT_TORNADO = "TORNADO";
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static final double TORNADO_MIN_RADIUS = 0.15;
    private static final String NAMEPLATE_BASE_HEIGHT_KEY = "npc.nameplate.base-height";
    private static final double DEFAULT_NAMEPLATE_BASE_HEIGHT = 1.8;
    private static final String SCALE_KEY = "scale";

    private static final class LineState {
        final HoloLine line;
        final Display display;
        final net.minecraft.world.entity.Entity handle;
        final int index;
        final double offset;
        final boolean block;
        final boolean dynamic;
        double x;
        double y;
        double z;
        float yaw;
        String rendered;

        LineState(HoloLine line, Display display, int index, double offset, boolean dynamic, String rendered) {
            this.line = line;
            this.display = display;
            this.handle = Packets.handle(display);
            this.index = index;
            this.offset = offset;
            this.block = HoloLine.BLOCK.equalsIgnoreCase(line.type);
            this.dynamic = dynamic;
            this.rendered = rendered;
        }
    }

    private final List<HoloLine> lines = new CopyOnWriteArrayList<>();
    private volatile List<LineState> states = List.of();
    private double baseX;
    private double baseY;
    private double baseZ;
    private float baseYaw;
    double lineSpacing;
    double spinSpeed;
    double bobHeight;
    double bobSpeed;
    double orbitRadius;
    double orbitSpeed;
    double pulseAmount;
    double pulseSpeed;
    double swayAngle;
    double swaySpeed;
    String layout = LAYOUT_STACK;
    double layoutRadius;
    double layoutSpeed;
    double helixStep;
    double waveHeight;
    double waveSpeed;
    volatile String attachTo = "";
    volatile double attachHeight;
    private volatile double attachScale = 1;

    public Hologram(Nexora plugin, String id, Location anchor) {
        super(plugin, id, anchor);
    }

    private static Map<String, Binding<Hologram>> createBindings() {
        Map<String, Binding<Hologram>> map = NexoraObject.commonBindings();
        map.put("line-spacing", Binding.number(h -> h.lineSpacing, (h, v) -> h.lineSpacing = v));
        map.put("spin-speed", Binding.number(h -> h.spinSpeed, (h, v) -> h.spinSpeed = v));
        map.put("bob-height", Binding.number(h -> h.bobHeight, (h, v) -> h.bobHeight = v));
        map.put("bob-speed", Binding.number(h -> h.bobSpeed, (h, v) -> h.bobSpeed = v));
        map.put("orbit-radius", Binding.number(h -> h.orbitRadius, (h, v) -> h.orbitRadius = v));
        map.put("orbit-speed", Binding.number(h -> h.orbitSpeed, (h, v) -> h.orbitSpeed = v));
        map.put("pulse-amount", Binding.number(h -> h.pulseAmount, (h, v) -> h.pulseAmount = v));
        map.put("pulse-speed", Binding.number(h -> h.pulseSpeed, (h, v) -> h.pulseSpeed = v));
        map.put("sway-angle", Binding.number(h -> h.swayAngle, (h, v) -> h.swayAngle = v));
        map.put("sway-speed", Binding.number(h -> h.swaySpeed, (h, v) -> h.swaySpeed = v));
        map.put("layout", Binding.text(h -> h.layout, (h, v) -> h.layout = v));
        map.put("layout-radius", Binding.number(h -> h.layoutRadius, (h, v) -> h.layoutRadius = v));
        map.put("layout-speed", Binding.number(h -> h.layoutSpeed, (h, v) -> h.layoutSpeed = v));
        map.put("helix-step", Binding.number(h -> h.helixStep, (h, v) -> h.helixStep = v));
        map.put("wave-height", Binding.number(h -> h.waveHeight, (h, v) -> h.waveHeight = v));
        map.put("wave-speed", Binding.number(h -> h.waveSpeed, (h, v) -> h.waveSpeed = v));
        map.put("attach-to", Binding.text(h -> h.attachTo, (h, v) -> h.attachTo = v == null ? "" : v.trim()));
        map.put("attach-height", Binding.number(h -> h.attachHeight, (h, v) -> h.attachHeight = v));
        return Collections.unmodifiableMap(map);
    }

    private double[] attachedCenter() {
        if (attachTo.isEmpty()) return null;
        var found = plugin.objects().get(NPC, attachTo);
        if (found.isEmpty() || found.get().isRemoved()) return null;
        var npc = found.get();
        double base = plugin.getConfig().getDouble(NAMEPLATE_BASE_HEIGHT_KEY, DEFAULT_NAMEPLATE_BASE_HEIGHT);
        return new double[]{npc.x(), npc.y() + base * attachScale + attachHeight, npc.z()};
    }

    private void refreshAttachScale() {
        if (attachTo.isEmpty()) return;
        var found = plugin.objects().get(NPC, attachTo);
        if (found.isEmpty()) return;
        try {
            attachScale = Double.parseDouble(found.get().values().getOrDefault(SCALE_KEY, "1"));
        } catch (NumberFormatException e) {
            attachScale = 1;
        }
    }

    public List<HoloLine> lines() {
        return lines;
    }

    @Override
    public String kind() {
        return HOLOGRAM;
    }

    @Override
    public Map<String, String> values() {
        return plugin.schema().read(BINDINGS, this);
    }

    @Override
    public List<String> apply(Map<String, String> values) {
        return plugin.schema().apply(kind(), BINDINGS, this, values);
    }

    @Override
    protected void writeExtra(Map<String, Object> out) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (var line : lines) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(PROPS_KEY, new LinkedHashMap<>(line.values(plugin.schema())));
            if (line.itemData() != null) entry.put(ITEM_KEY, line.itemData());
            list.add(entry);
        }
        out.put(LINES_KEY, list);
    }

    @Override
    protected void readExtra(ConfigurationSection section) {
        if (!section.contains(LINES_KEY)) return;
        List<HoloLine> restored = new ArrayList<>();
        for (var entry : section.getMapList(LINES_KEY)) {
            var line = new HoloLine();
            line.apply(plugin.schema(), plugin.objects().defaults(LINE));
            if (entry.get(PROPS_KEY) instanceof Map<?, ?> props) {
                Map<String, String> values = new LinkedHashMap<>();
                props.forEach((key, value) -> values.put(String.valueOf(key), String.valueOf(value)));
                line.apply(plugin.schema(), values);
            }
            if (entry.get(ITEM_KEY) instanceof String item) line.itemData(item);
            restored.add(line);
        }
        lines.clear();
        lines.addAll(restored);
    }

    @Override
    protected Built build(Location anchor) {
        var world = anchor.getWorld();
        if (world == null) throw new IllegalStateException(id());
        var config = plugin.getConfig();
        int interval = Math.max(1, config.getInt("animation.interval-ticks"));
        float range = (float) (viewRange / config.getDouble("display.view-range-unit"));
        var fallback = fallbackMaterial(config);
        refreshAttachScale();
        var attached = attachedCenter();
        baseX = attached == null ? anchor.getX() : attached[0];
        baseY = attached == null ? anchor.getY() : attached[1];
        baseZ = attached == null ? anchor.getZ() : attached[2];
        if (attached != null) setPosition(baseX, baseY, baseZ);
        baseYaw = anchor.getYaw();
        var source = List.copyOf(lines);
        int count = source.size();
        double seconds = ticks / TICKS_PER_SECOND;
        List<LineState> built = new ArrayList<>(count);
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            var line = source.get(i).copy();
            double offset = (count - 1 - i) * lineSpacing + line.offsetY;
            var place = place(line, i, offset, count, baseX, baseY, baseZ, seconds);
            var location = new Location(world, place[0], place[1], place[2], (float) place[3], 0f);
            var rendered = HoloLine.TEXT.equalsIgnoreCase(line.type) ? TextEffects.render(config, line, ticks) : "";
            var display = template(world, location, line, rendered, interval, range, fallback);
            var state = new LineState(line, display, i, offset, TextEffects.dynamic(config, line), rendered);
            state.x = place[0];
            state.y = place[1];
            state.z = place[2];
            state.yaw = (float) place[3];
            built.add(state);
            ids[i] = display.getEntityId();
        }
        states = List.copyOf(built);
        List<Packet<?>> despawn = new ArrayList<>();
        if (count > 0) despawn.add(Packets.destroy(ids));
        var spawn = spawnPackets();
        for (var state : built) Packets.clearDirty(state.handle);
        return new Built(spawn, despawn, ids);
    }

    @Override
    protected void tick(int interval) {
        ticks += interval;
        var config = plugin.getConfig();
        double seconds = ticks / TICKS_PER_SECOND;
        var path = pathPoint(seconds);
        var attached = attachedCenter();
        var origin = attached != null ? attached : path;
        double centerX = origin == null ? baseX : origin[0];
        double centerY = origin == null ? baseY : origin[1];
        double centerZ = origin == null ? baseZ : origin[2];
        if (bobHeight > 0) centerY += bobHeight * Math.sin(Math.TAU * bobSpeed * seconds);
        if (orbitRadius > 0) {
            double angle = Math.TAU * orbitSpeed * seconds;
            centerX += orbitRadius * Math.cos(angle);
            centerZ += orbitRadius * Math.sin(angle);
        }
        boolean shaped = !LAYOUT_STACK.equals(layout) && layoutSpeed != 0;
        boolean moving = path != null || attached != null || bobHeight > 0 || orbitRadius > 0 || waveHeight > 0 || shaped;
        boolean transforming = spinSpeed != 0 || swayAngle > 0 || pulseAmount > 0;
        float spin = (float) Math.toRadians((spinSpeed * seconds) % FULL_TURN);
        float sway = (float) Math.toRadians(swayAngle * Math.sin(Math.TAU * swaySpeed * seconds));
        float pulse = (float) (1 + pulseAmount * Math.sin(Math.TAU * pulseSpeed * seconds));
        int count = states.size();
        List<Packet<?>> packets = new ArrayList<>();
        for (var state : states) {
            if (moving) {
                var place = place(state.line, state.index, state.offset, count, centerX, centerY, centerZ, seconds);
                if (place[0] != state.x || place[1] != state.y || place[2] != state.z || (float) place[3] != state.yaw) {
                    state.x = place[0];
                    state.y = place[1];
                    state.z = place[2];
                    state.yaw = (float) place[3];
                    packets.add(Packets.teleport(state.handle.getId(), state.x, state.y, state.z, state.yaw, 0f));
                }
            }
            update(state, config, transforming, spin, sway, pulse, packets);
        }
        setPosition(centerX, centerY, centerZ);
        if (!packets.isEmpty()) {
            broadcast(packets);
        }
        particles(centerX, centerY, centerZ, interval);
    }

    private double[] place(HoloLine line, int index, double stackOffset, int count, double cx, double cy, double cz, double seconds) {
        var position = layoutPlace(line, index, stackOffset, count, cx, cy, cz, seconds);
        if (line.offsetX != 0 || line.offsetZ != 0) {
            double yawRad = Math.toRadians(baseYaw);
            double sin = Math.sin(yawRad);
            double cos = Math.cos(yawRad);
            position[0] += -cos * line.offsetX - sin * line.offsetZ;
            position[2] += -sin * line.offsetX + cos * line.offsetZ;
        }
        return position;
    }

    private double[] layoutPlace(HoloLine line, int index, double stackOffset, int count, double cx, double cy, double cz, double seconds) {
        double y = cy;
        if (waveHeight > 0) y += waveHeight * Math.sin(Math.TAU * waveSpeed * seconds + Math.TAU * index / Math.max(1, count));
        double turn = Math.toRadians(layoutSpeed * seconds);
        return switch (layout) {
            case LAYOUT_RING -> circle(cx, y + line.offsetY, cz, layoutRadius, Math.TAU * index / Math.max(1, count) + turn);
            case LAYOUT_WHEEL -> wheel(cx, y + line.offsetY, cz, Math.TAU * index / Math.max(1, count) + turn);
            case LAYOUT_SPHERE -> sphere(cx, y + line.offsetY, cz, index, count, turn);
            case LAYOUT_TORNADO -> {
                double level = count <= 1 ? 1 : (count - 1 - index) / (double) (count - 1);
                yield circle(cx, y + (count - 1 - index) * lineSpacing + line.offsetY, cz, layoutRadius * Math.max(TORNADO_MIN_RADIUS, level), Math.toRadians(helixStep) * index + turn);
            }
            case LAYOUT_HELIX -> circle(cx, y + (count - 1 - index) * lineSpacing + line.offsetY, cz, layoutRadius, Math.toRadians(helixStep) * index + turn);
            case LAYOUT_ROW -> {
                double yawRad = Math.toRadians(baseYaw);
                double along = (index - (count - 1) / 2.0) * lineSpacing;
                yield new double[]{cx - Math.cos(yawRad) * along, y + line.offsetY, cz - Math.sin(yawRad) * along, baseYaw};
            }
            default -> new double[]{cx, y + stackOffset, cz, baseYaw};
        };
    }

    private double[] wheel(double cx, double cy, double cz, double angle) {
        double yawRad = Math.toRadians(baseYaw);
        double side = layoutRadius * Math.cos(angle);
        return new double[]{cx - Math.cos(yawRad) * side, cy + layoutRadius * Math.sin(angle), cz - Math.sin(yawRad) * side, baseYaw};
    }

    private double[] sphere(double cx, double cy, double cz, int index, int count, double turn) {
        double unitY = 1 - 2 * (index + 0.5) / Math.max(1, count);
        double ring = Math.sqrt(Math.max(0, 1 - unitY * unitY));
        double theta = GOLDEN_ANGLE * index + turn;
        double dx = Math.cos(theta) * ring, dz = Math.sin(theta) * ring;
        return new double[]{cx + layoutRadius * dx, cy + layoutRadius * unitY, cz + layoutRadius * dz, Math.toDegrees(Math.atan2(-dx, dz))};
    }

    private double[] circle(double cx, double y, double cz, double radius, double angle) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        return new double[]{cx + radius * dx, y, cz + radius * dz, Math.toDegrees(Math.atan2(-dx, dz))};
    }

    private void update(LineState state, FileConfiguration config, boolean transforming, float spin, float sway, float pulse, List<Packet<?>> packets) {
        var handle = state.handle;
        if (transforming) state.display.setTransformation(transformation(state.block, state.line, (float) state.line.scale * pulse, spin, sway));
        if (state.dynamic && state.display instanceof TextDisplay text) {
            var rendered = TextEffects.render(config, state.line, ticks);
            if (!rendered.equals(state.rendered)) {
                state.rendered = rendered;
                text.text(ColorUtil.colorize(rendered));
            }
        }
        var data = Packets.dirtyData(handle, transforming);
        if (data != null) packets.add(data);
    }

    private List<Packet<?>> spawnPackets() {
        List<Packet<?>> list = new ArrayList<>();
        for (var state : states) {
            var handle = state.handle;
            list.add(Packets.spawn(handle, state.x, state.y, state.z, state.yaw, 0f));
            list.add(Packets.fullData(handle));
        }
        return list;
    }

    @Override
    protected List<Packet<?>> catchUp() {
        List<Packet<?>> list = new ArrayList<>();
        for (var state : states) {
            list.add(Packets.teleport(state.handle.getId(), state.x, state.y, state.z, state.yaw, 0f));
            var data = Packets.fullData(state.handle);
            if (data != null) list.add(data);
        }
        return list;
    }

    private Display template(World world, Location location, HoloLine line, String rendered, int interval, float range, Material fallback) {
        var type = line.type.toUpperCase(Locale.ROOT);
        Display display = switch (type) {
            case HoloLine.ITEM -> itemDisplay(world, location, line, fallback);
            case HoloLine.BLOCK -> blockDisplay(world, location, line, fallback);
            default -> textDisplay(world, location, line, rendered);
        };
        display.setBillboard(parse(Display.Billboard.class, line.billboard, Display.Billboard.CENTER));
        display.setTransformation(transformation(HoloLine.BLOCK.equals(type), line, (float) line.scale, 0f, 0f));
        display.setInterpolationDuration(interval);
        display.setTeleportDuration(Math.min(interval, MAX_TELEPORT_DURATION));
        display.setViewRange(range);
        if (line.brightness >= 0) display.setBrightness(new Display.Brightness(line.brightness, line.brightness));
        if (line.glow) {
            display.setGlowing(true);
            display.setGlowColorOverride(Color.fromRGB(TextEffects.argb(line.glowColor) & RGB_MASK));
        }
        return display;
    }

    private TextDisplay textDisplay(World world, Location location, HoloLine line, String rendered) {
        var display = world.createEntity(location, TextDisplay.class);
        display.text(ColorUtil.colorize(rendered));
        display.setLineWidth(line.lineWidth);
        if (line.backgroundDefault) {
            display.setDefaultBackground(true);
        } else {
            display.setBackgroundColor(Color.fromARGB(TextEffects.argb(line.background)));
        }
        display.setShadowed(line.shadow);
        display.setSeeThrough(line.seeThrough);
        display.setAlignment(parse(TextDisplay.TextAlignment.class, line.alignment, TextDisplay.TextAlignment.CENTER));
        display.setTextOpacity((byte) line.opacity);
        return display;
    }

    private ItemDisplay itemDisplay(World world, Location location, HoloLine line, Material fallback) {
        var display = world.createEntity(location, ItemDisplay.class);
        ItemStack stack = ItemCodec.decode(line.itemData()).filter(item -> !item.isEmpty())
            .orElseGet(() -> new ItemStack(material(line.material, fallback, false)));
        display.setItemStack(stack);
        display.setItemDisplayTransform(parse(ItemDisplay.ItemDisplayTransform.class, line.itemTransform, ItemDisplay.ItemDisplayTransform.FIXED));
        return display;
    }

    private BlockDisplay blockDisplay(World world, Location location, HoloLine line, Material fallback) {
        var display = world.createEntity(location, BlockDisplay.class);
        display.setBlock(material(line.material, fallback, true).createBlockData());
        return display;
    }

    private static Material fallbackMaterial(FileConfiguration config) {
        var material = Material.matchMaterial(config.getString("display.fallback-material", ""));
        return material == null ? Material.STONE : material;
    }

    private static Material material(String name, Material fallback, boolean block) {
        var material = name == null ? null : Material.matchMaterial(name.trim());
        if (material == null || material.isAir()) return fallback;
        return (block ? material.isBlock() : material.isItem()) ? material : fallback;
    }

    private static Transformation transformation(boolean block, HoloLine line, float scale, float spin, float sway) {
        var rotation = new Quaternionf().rotationY(spin).rotateZ(sway)
            .rotateY((float) Math.toRadians(line.rotY))
            .rotateX((float) Math.toRadians(line.rotX))
            .rotateZ((float) Math.toRadians(line.rotZ));
        var size = new Vector3f(scale, scale, scale);
        var translation = block ? new Vector3f(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER).mul(size).rotate(rotation) : new Vector3f();
        return new Transformation(translation, rotation, size, new Quaternionf());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
