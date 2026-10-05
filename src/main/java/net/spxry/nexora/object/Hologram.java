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

    private static final class LineState {
        final HoloLine line;
        final Display display;
        final net.minecraft.world.entity.Entity handle;
        final double offset;
        final boolean block;
        final boolean dynamic;
        double x;
        double y;
        double z;
        String rendered;

        LineState(HoloLine line, Display display, double offset, boolean dynamic, String rendered) {
            this.line = line;
            this.display = display;
            this.handle = Packets.handle(display);
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
        return Collections.unmodifiableMap(map);
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
        baseX = anchor.getX();
        baseY = anchor.getY();
        baseZ = anchor.getZ();
        baseYaw = anchor.getYaw();
        var source = List.copyOf(lines);
        int count = source.size();
        List<LineState> built = new ArrayList<>(count);
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            var line = source.get(i).copy();
            double offset = (count - 1 - i) * lineSpacing + line.offsetY;
            var location = new Location(world, baseX, baseY + offset, baseZ, baseYaw, 0f);
            var rendered = HoloLine.TEXT.equalsIgnoreCase(line.type) ? TextEffects.render(config, line, ticks) : "";
            var display = template(world, location, line, rendered, interval, range, fallback);
            var state = new LineState(line, display, offset, TextEffects.dynamic(config, line), rendered);
            state.x = baseX;
            state.y = baseY + offset;
            state.z = baseZ;
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
        double centerX = path == null ? baseX : path[0];
        double centerY = path == null ? baseY : path[1];
        double centerZ = path == null ? baseZ : path[2];
        if (bobHeight > 0) centerY += bobHeight * Math.sin(Math.TAU * bobSpeed * seconds);
        if (orbitRadius > 0) {
            double angle = Math.TAU * orbitSpeed * seconds;
            centerX += orbitRadius * Math.cos(angle);
            centerZ += orbitRadius * Math.sin(angle);
        }
        boolean moving = path != null || bobHeight > 0 || orbitRadius > 0;
        boolean transforming = spinSpeed != 0 || swayAngle > 0 || pulseAmount > 0;
        float spin = (float) Math.toRadians((spinSpeed * seconds) % FULL_TURN);
        float sway = (float) Math.toRadians(swayAngle * Math.sin(Math.TAU * swaySpeed * seconds));
        float pulse = (float) (1 + pulseAmount * Math.sin(Math.TAU * pulseSpeed * seconds));
        List<Packet<?>> packets = new ArrayList<>();
        for (var state : states) {
            update(state, config, moving, transforming, centerX, centerY, centerZ, spin, sway, pulse, packets);
        }
        setPosition(centerX, centerY, centerZ);
        if (!packets.isEmpty()) {
            broadcast(packets);
        }
        particles(centerX, centerY, centerZ, interval);
    }

    private void update(LineState state, FileConfiguration config, boolean moving, boolean transforming, double centerX, double centerY, double centerZ, float spin, float sway, float pulse, List<Packet<?>> packets) {
        var handle = state.handle;
        double nx = centerX;
        double ny = centerY + state.offset;
        double nz = centerZ;
        if (moving && (nx != state.x || ny != state.y || nz != state.z)) {
            state.x = nx;
            state.y = ny;
            state.z = nz;
            packets.add(Packets.teleport(state.display.getEntityId(), nx, ny, nz, baseYaw, 0f));
        }
        if (transforming) state.display.setTransformation(transformation(state.block, (float) state.line.scale * pulse, spin, sway));
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
            list.add(Packets.spawn(handle, state.x, state.y, state.z, baseYaw, 0f));
            list.add(Packets.fullData(handle));
        }
        return list;
    }

    @Override
    protected List<Packet<?>> catchUp() {
        List<Packet<?>> list = new ArrayList<>();
        for (var state : states) {
            list.add(Packets.teleport(state.handle.getId(), state.x, state.y, state.z, baseYaw, 0f));
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
        display.setTransformation(transformation(HoloLine.BLOCK.equals(type), (float) line.scale, 0f, 0f));
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

    private static Transformation transformation(boolean block, float scale, float spin, float sway) {
        var rotation = new Quaternionf().rotationY(spin).rotateZ(sway);
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
