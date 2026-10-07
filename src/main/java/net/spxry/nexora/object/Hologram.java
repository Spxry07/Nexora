package net.spxry.nexora.object;

import net.minecraft.network.protocol.Packet;
import net.spxry.nexora.Nexora;
import net.spxry.nexora.edit.Binding;
import net.spxry.nexora.npc.HasRuntime;
import net.spxry.nexora.npc.TriggerHost;
import net.spxry.nexora.npc.TriggerRuntime;
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
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class Hologram extends NexoraObject implements TriggerHost, HasRuntime {
    public static final Map<String, Binding<Hologram>> BINDINGS = createBindings();

    private enum ClickType { ANY, RIGHT, LEFT }

    private record Eye(UUID world, double x, double y, double z) {}

    private record Nearest(Eye eye, double distance) {}

    private record Extent(double cx, double cy, double cz, double top, double bottom) {}

    private static final String LINES_KEY = "lines";
    private static final String PROPS_KEY = "props";
    private static final String ITEM_KEY = "item";
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double FULL_TURN = 360.0;
    private static final double HALF_TURN = 180.0;
    private static final int MAX_TELEPORT_DURATION = 59;
    private static final float BLOCK_CENTER = -0.5f;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final String LAYOUT_STACK = "STACK";
    private static final String NAMEPLATE_BASE_HEIGHT_KEY = "npc.nameplate.base-height";
    private static final double DEFAULT_NAMEPLATE_BASE_HEIGHT = 1.8;
    private static final String SCALE_KEY = "scale";
    private static final long MILLIS_PER_TICK = 50L;
    private static final double EPSILON = 1.0E-4;
    private static final String EASE_RATE_KEY = "reactive.ease-rate";
    private static final double DEFAULT_EASE_RATE = 5.0;
    private static final String TURN_SPEED_KEY = "reactive.face-turn-speed";
    private static final double DEFAULT_TURN_SPEED = 360.0;
    private static final String MORPH_TICKS_KEY = "reactive.morph-ticks";
    private static final int DEFAULT_MORPH_TICKS = 40;
    private static final String PULSE_AMOUNT_KEY = "reactive.pulse-amount";
    private static final double DEFAULT_PULSE_AMOUNT = 0.25;
    private static final String PULSE_TICKS_KEY = "reactive.pulse-ticks";
    private static final int DEFAULT_PULSE_TICKS = 10;
    private static final String PLACEHOLDER_PLAYER = "player";
    private static final String PLACEHOLDER_HOLOGRAM = "hologram";
    private static final String PLACEHOLDER_UUID = "uuid";
    private static final String VERB_TEXT = "text";
    private static final String VERB_LAYOUT = "layout";
    private static final String VERB_SPIN = "spin";
    private static final String VERB_PULSE = "pulse";
    private static final String VERB_SCALE = "scale";
    private static final String VERB_MOVE = "move";
    private static final String VERB_GLOW = "glow";
    private static final String VERB_HIDE = "hide";
    private static final String VERB_SHOW = "show";
    private static final String VERB_EFFECT = "effect";
    private static final String GLOW_ON = "on";
    private static final String GLOW_OFF = "off";
    private static final String GLOW_TOGGLE = "toggle";
    private static final String ANCHOR_CENTER = "center";
    private static final String ANCHOR_TOP = "top";
    private static final String ANCHOR_BOTTOM = "bottom";
    private static final String ANCHOR_ABOVE = "above";
    private static final String ANCHOR_LINE = "line:";
    private static final String MOVE_SEPARATOR = ",";
    private static final int MOVE_PARTS = 3;
    private static final String ARG_JOINER = " ";
    private static final int ARG_FIRST = 0;
    private static final int ARG_SECOND = 1;
    private static final int ARGS_PAIR = 2;
    private static final String EFFECT_PROP = "effect";
    private static final String LAYOUT_PROP = "layout";

    private static final class LineState {
        final HoloLine line;
        final Display display;
        final net.minecraft.world.entity.Entity handle;
        final int index;
        final boolean block;
        boolean dynamic;
        boolean refresh;
        double x;
        double y;
        double z;
        float yaw;
        String rendered;

        LineState(HoloLine line, Display display, int index, boolean dynamic, String rendered) {
            this.line = line;
            this.display = display;
            this.handle = Packets.handle(display);
            this.index = index;
            this.block = HoloLine.BLOCK.equalsIgnoreCase(line.type);
            this.dynamic = dynamic;
            this.rendered = rendered;
        }
    }

    private final List<HoloLine> lines = new CopyOnWriteArrayList<>();
    private final TriggerRuntime runtime;
    private final Map<UUID, Eye> eyes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private volatile List<LineState> states = List.of();
    private volatile Extent extent = new Extent(0, 0, 0, 0, 0);
    private volatile boolean hidden;
    private volatile double facing;
    private Interaction hitbox;
    private net.minecraft.world.entity.Entity hitboxHandle;
    private double hitX;
    private double hitY;
    private double hitZ;
    private UUID worldId;
    private double baseX;
    private double baseY;
    private double baseZ;
    private float baseYaw;
    private double currentYaw;
    private String layoutOverride;
    private long morphUntil;
    private boolean spinOverridden;
    private double spinOverride;
    private long spinUntil;
    private double spinAngle;
    private double pulseBurst;
    private long pulseStart;
    private long pulseEnd;
    private double scaleFactor = 1;
    private double scaleTarget = 1;
    private long scaleUntil;
    private double moveX;
    private double moveY;
    private double moveZ;
    private double moveTargetX;
    private double moveTargetY;
    private double moveTargetZ;
    private long moveUntil;
    private double visibility = 1;
    private long hideUntil;
    private Boolean glowOverride;
    private double followX;
    private double followZ;
    private double proximity = 1;
    private double lastFactor = 1;
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
    double shapeWidth = 1;
    double shapeHeight = 1;
    double shapeTilt;
    int shapeSides = 5;
    double shapeInner = 0.45;
    int gridColumns = 3;
    double arcAngle = 180;
    boolean clickable;
    double hitboxWidth = 1.5;
    double hitboxHeight = 1;
    double hitboxOffsetY;
    boolean facePlayer;
    boolean followPlayer;
    double followRange = 12;
    double followDistance = 2.5;
    double followSpeed = 2;
    double proximityScale;
    double proximityRange = 6;
    volatile String actions = "";
    volatile String clickType = "ANY";
    volatile int cooldown = 20;
    volatile String triggers = "";
    volatile double approachRange = 5;
    volatile double lookTriggerRange = 8;
    volatile String attachTo = "";
    volatile double attachHeight;
    private volatile double attachScale = 1;

    public Hologram(Nexora plugin, String id, Location anchor) {
        super(plugin, id, anchor);
        this.runtime = new TriggerRuntime(plugin, this);
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
        map.put("shape-width", Binding.number(h -> h.shapeWidth, (h, v) -> h.shapeWidth = v));
        map.put("shape-height", Binding.number(h -> h.shapeHeight, (h, v) -> h.shapeHeight = v));
        map.put("shape-tilt", Binding.number(h -> h.shapeTilt, (h, v) -> h.shapeTilt = v));
        map.put("shape-sides", Binding.integer(h -> h.shapeSides, (h, v) -> h.shapeSides = v));
        map.put("shape-inner", Binding.number(h -> h.shapeInner, (h, v) -> h.shapeInner = v));
        map.put("grid-columns", Binding.integer(h -> h.gridColumns, (h, v) -> h.gridColumns = v));
        map.put("arc-angle", Binding.number(h -> h.arcAngle, (h, v) -> h.arcAngle = v));
        map.put("clickable", Binding.bool(h -> h.clickable, (h, v) -> h.clickable = v));
        map.put("hitbox-width", Binding.number(h -> h.hitboxWidth, (h, v) -> h.hitboxWidth = v));
        map.put("hitbox-height", Binding.number(h -> h.hitboxHeight, (h, v) -> h.hitboxHeight = v));
        map.put("hitbox-offset-y", Binding.number(h -> h.hitboxOffsetY, (h, v) -> h.hitboxOffsetY = v));
        map.put("actions", Binding.text(h -> h.actions, (h, v) -> h.actions = v == null ? "" : v));
        map.put("click-type", Binding.text(h -> h.clickType, (h, v) -> h.clickType = v == null ? "" : v));
        map.put("cooldown", Binding.integer(h -> h.cooldown, (h, v) -> h.cooldown = v));
        map.put("triggers", Binding.text(h -> h.triggers, (h, v) -> h.triggers = v == null ? "" : v));
        map.put("approach-range", Binding.number(h -> h.approachRange, (h, v) -> h.approachRange = v));
        map.put("look-trigger-range", Binding.number(h -> h.lookTriggerRange, (h, v) -> h.lookTriggerRange = v));
        map.put("face-player", Binding.bool(h -> h.facePlayer, (h, v) -> h.facePlayer = v));
        map.put("follow-player", Binding.bool(h -> h.followPlayer, (h, v) -> h.followPlayer = v));
        map.put("follow-range", Binding.number(h -> h.followRange, (h, v) -> h.followRange = v));
        map.put("follow-distance", Binding.number(h -> h.followDistance, (h, v) -> h.followDistance = v));
        map.put("follow-speed", Binding.number(h -> h.followSpeed, (h, v) -> h.followSpeed = v));
        map.put("proximity-scale", Binding.number(h -> h.proximityScale, (h, v) -> h.proximityScale = v));
        map.put("proximity-range", Binding.number(h -> h.proximityRange, (h, v) -> h.proximityRange = v));
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
    public TriggerRuntime runtime() {
        return runtime;
    }

    @Override
    public double lookTriggerRange() {
        return lookTriggerRange;
    }

    @Override
    public double approachTriggerRange() {
        return approachRange;
    }

    @Override
    public float facingYaw() {
        return (float) facing;
    }

    @Override
    public Vector anchorPoint(String anchor) {
        if (anchor == null) return null;
        var name = anchor.toLowerCase(Locale.ROOT);
        var bounds = extent;
        return switch (name) {
            case ANCHOR_CENTER -> new Vector(bounds.cx(), bounds.cy(), bounds.cz());
            case ANCHOR_TOP -> new Vector(bounds.cx(), bounds.top(), bounds.cz());
            case ANCHOR_BOTTOM -> new Vector(bounds.cx(), bounds.bottom(), bounds.cz());
            case ANCHOR_ABOVE -> new Vector(bounds.cx(), bounds.top() + lineSpacing, bounds.cz());
            default -> name.startsWith(ANCHOR_LINE) ? linePoint(name.substring(ANCHOR_LINE.length())) : null;
        };
    }

    private Vector linePoint(String raw) {
        try {
            int index = Integer.parseInt(raw.trim()) - 1;
            var list = states;
            if (index < 0 || index >= list.size()) return null;
            var state = list.get(index);
            return new Vector(state.x, state.y, state.z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void viewerAt(UUID uuid, Location eye) {
        var world = eye.getWorld();
        if (world == null) return;
        eyes.put(uuid, new Eye(world.getUID(), eye.getX(), eye.getY(), eye.getZ()));
    }

    public void click(Player player, boolean attack) {
        if (hidden) return;
        runActions(player, attack);
        runtime.click(player, attack);
    }

    private void runActions(Player player, boolean attack) {
        var script = actions;
        if (script.isBlank() || !accepts(attack)) return;
        var uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        cooldowns.values().removeIf(until -> until <= now);
        if (cooldowns.containsKey(uuid)) return;
        int delay = cooldown;
        if (delay > 0) cooldowns.put(uuid, now + delay * MILLIS_PER_TICK);
        plugin.actions().run(player, script, Map.of(PLACEHOLDER_PLAYER, player.getName(), PLACEHOLDER_HOLOGRAM, id(), PLACEHOLDER_UUID, uuid.toString()));
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
    protected void onHide(UUID viewer) {
        eyes.remove(viewer);
        runtime.forget(viewer);
    }

    @Override
    public boolean hostAction(String verb, List<String> args, Player player) {
        if (verb == null || args == null) return false;
        Runnable command = switch (verb.toLowerCase(Locale.ROOT)) {
            case VERB_TEXT -> textCommand(args);
            case VERB_EFFECT -> effectCommand(args);
            case VERB_LAYOUT -> layoutCommand(args);
            case VERB_SPIN -> spinCommand(args);
            case VERB_PULSE -> pulseCommand(args);
            case VERB_SCALE -> scaleCommand(args);
            case VERB_MOVE -> moveCommand(args);
            case VERB_GLOW -> glowCommand(args);
            case VERB_HIDE -> hideCommand(args);
            case VERB_SHOW -> () -> setHidden(false, 0);
            default -> null;
        };
        if (command == null) return false;
        commands.add(command);
        return true;
    }

    private Runnable textCommand(List<String> args) {
        if (args.size() < ARGS_PAIR) return null;
        var index = parseInt(args.get(ARG_FIRST), 0) - 1;
        var text = String.join(ARG_JOINER, args.subList(ARG_SECOND, args.size()));
        return () -> editLine(index, line -> line.text = text);
    }

    private Runnable effectCommand(List<String> args) {
        if (args.size() < ARGS_PAIR) return null;
        var index = parseInt(args.get(ARG_FIRST), 0) - 1;
        var name = args.get(ARG_SECOND).toUpperCase(Locale.ROOT);
        if (!option(LINE, EFFECT_PROP, name)) return null;
        return () -> editLine(index, line -> line.effect = name);
    }

    private Runnable layoutCommand(List<String> args) {
        if (args.isEmpty()) return null;
        var name = args.get(ARG_FIRST).toUpperCase(Locale.ROOT);
        if (!option(HOLOGRAM, LAYOUT_PROP, name)) return null;
        int morph = plugin.getConfig().getInt(MORPH_TICKS_KEY, DEFAULT_MORPH_TICKS);
        return () -> {
            layoutOverride = name;
            morphUntil = ticks + morph;
        };
    }

    private Runnable spinCommand(List<String> args) {
        if (args.isEmpty()) return null;
        var speed = parseDouble(args.get(ARG_FIRST));
        if (speed == null) return null;
        int duration = args.size() > ARG_SECOND ? parseInt(args.get(ARG_SECOND), 0) : 0;
        return () -> {
            spinOverridden = true;
            spinOverride = speed;
            spinUntil = duration > 0 ? ticks + duration : 0;
        };
    }

    private Runnable pulseCommand(List<String> args) {
        var config = plugin.getConfig();
        var parsed = args.isEmpty() ? null : parseDouble(args.get(ARG_FIRST));
        double amount = parsed == null ? config.getDouble(PULSE_AMOUNT_KEY, DEFAULT_PULSE_AMOUNT) : parsed;
        int configured = config.getInt(PULSE_TICKS_KEY, DEFAULT_PULSE_TICKS);
        int duration = Math.max(1, args.size() > ARG_SECOND ? parseInt(args.get(ARG_SECOND), configured) : configured);
        return () -> {
            pulseBurst = amount;
            pulseStart = ticks;
            pulseEnd = ticks + duration;
        };
    }

    private Runnable scaleCommand(List<String> args) {
        if (args.isEmpty()) return null;
        var factor = parseDouble(args.get(ARG_FIRST));
        if (factor == null) return null;
        int duration = args.size() > ARG_SECOND ? parseInt(args.get(ARG_SECOND), 0) : 0;
        return () -> {
            scaleTarget = Math.max(0, factor);
            scaleUntil = duration > 0 ? ticks + duration : 0;
        };
    }

    private Runnable moveCommand(List<String> args) {
        if (args.isEmpty()) return null;
        var parts = args.get(ARG_FIRST).split(MOVE_SEPARATOR);
        if (parts.length != MOVE_PARTS) return null;
        var dx = parseDouble(parts[0]);
        var dy = parseDouble(parts[1]);
        var dz = parseDouble(parts[2]);
        if (dx == null || dy == null || dz == null) return null;
        int duration = args.size() > ARG_SECOND ? parseInt(args.get(ARG_SECOND), 0) : 0;
        return () -> {
            moveTargetX = dx;
            moveTargetY = dy;
            moveTargetZ = dz;
            moveUntil = duration > 0 ? ticks + duration : 0;
        };
    }

    private Runnable glowCommand(List<String> args) {
        var mode = args.isEmpty() ? GLOW_TOGGLE : args.get(ARG_FIRST).toLowerCase(Locale.ROOT);
        return switch (mode) {
            case GLOW_ON -> () -> applyGlow(true);
            case GLOW_OFF -> () -> applyGlow(false);
            case GLOW_TOGGLE -> () -> applyGlow(!glowing());
            default -> null;
        };
    }

    private Runnable hideCommand(List<String> args) {
        int duration = args.isEmpty() ? 0 : parseInt(args.get(ARG_FIRST), 0);
        return () -> setHidden(true, duration);
    }

    private void setHidden(boolean value, int duration) {
        hidden = value;
        hideUntil = value && duration > 0 ? ticks + duration : 0;
    }

    private boolean glowing() {
        if (glowOverride != null) return glowOverride;
        for (var state : states) if (state.line.glow) return true;
        return false;
    }

    private void applyGlow(boolean on) {
        glowOverride = on;
        for (var state : states) {
            state.display.setGlowing(on);
            if (on) state.display.setGlowColorOverride(Color.fromRGB(TextEffects.argb(state.line.glowColor) & RGB_MASK));
        }
    }

    private void editLine(int index, Consumer<HoloLine> change) {
        var list = states;
        if (index < 0 || index >= list.size()) return;
        var state = list.get(index);
        if (!(state.display instanceof TextDisplay)) return;
        change.accept(state.line);
        state.dynamic = TextEffects.dynamic(plugin.getConfig(), state.line);
        state.refresh = true;
    }

    private boolean option(String kind, String prop, String value) {
        var found = plugin.schema().props(kind).get(prop);
        if (found == null || found.options() == null) return false;
        return found.options().stream().anyMatch(option -> option.id().equalsIgnoreCase(value));
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
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

    private void resetRuntime() {
        commands.clear();
        layoutOverride = null;
        morphUntil = 0;
        spinOverridden = false;
        spinUntil = 0;
        spinAngle = 0;
        pulseEnd = 0;
        scaleFactor = 1;
        scaleTarget = 1;
        scaleUntil = 0;
        moveX = 0;
        moveY = 0;
        moveZ = 0;
        moveTargetX = 0;
        moveTargetY = 0;
        moveTargetZ = 0;
        moveUntil = 0;
        visibility = 1;
        hidden = false;
        hideUntil = 0;
        glowOverride = null;
        followX = 0;
        followZ = 0;
        proximity = 1;
        lastFactor = 1;
    }

    @Override
    protected Built build(Location anchor) {
        var world = anchor.getWorld();
        if (world == null) throw new IllegalStateException(id());
        worldId = world.getUID();
        resetRuntime();
        runtime.rules(triggers);
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
        currentYaw = baseYaw;
        facing = baseYaw;
        var source = List.copyOf(lines);
        int count = source.size();
        double seconds = ticks / TICKS_PER_SECOND;
        var params = shapeParams(count);
        double turn = Math.toRadians(layoutSpeed * seconds);
        List<LineState> built = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            var line = source.get(i).copy();
            var place = place(params, layout, line, i, count, baseX, baseY, baseZ, seconds, turn);
            var location = new Location(world, place[0], place[1], place[2], (float) place[3], 0f);
            var rendered = HoloLine.TEXT.equalsIgnoreCase(line.type) ? TextEffects.render(config, line, ticks) : "";
            var display = template(world, location, line, rendered, interval, range, fallback);
            var state = new LineState(line, display, i, TextEffects.dynamic(config, line), rendered);
            state.x = place[0];
            state.y = place[1];
            state.z = place[2];
            state.yaw = (float) place[3];
            built.add(state);
        }
        states = List.copyOf(built);
        extent = extentOf(baseX, baseY, baseZ);
        buildHitbox(world);
        int[] ids = collectIds(built);
        List<Packet<?>> despawn = new ArrayList<>();
        if (ids.length > 0) despawn.add(Packets.destroy(ids));
        var spawn = spawnPackets();
        for (var state : built) Packets.clearDirty(state.handle);
        if (hitboxHandle != null) Packets.clearDirty(hitboxHandle);
        return new Built(spawn, despawn, ids);
    }

    private int[] collectIds(List<LineState> built) {
        int count = built.size();
        int[] ids = new int[count + (hitboxHandle == null ? 0 : 1)];
        for (int i = 0; i < count; i++) ids[i] = built.get(i).display.getEntityId();
        if (hitboxHandle != null) ids[count] = hitboxHandle.getId();
        return ids;
    }

    private void buildHitbox(World world) {
        hitbox = null;
        hitboxHandle = null;
        if (!clickable) return;
        var bounds = extent;
        hitX = bounds.cx();
        hitY = hitboxBottom(bounds);
        hitZ = bounds.cz();
        hitbox = world.createEntity(new Location(world, hitX, hitY, hitZ), Interaction.class);
        hitbox.setInteractionWidth((float) hitboxWidth);
        hitbox.setInteractionHeight((float) hitboxHeight);
        hitbox.setResponsive(true);
        hitboxHandle = Packets.handle(hitbox);
    }

    private double hitboxBottom(Extent bounds) {
        return bounds.cy() + hitboxOffsetY - hitboxHeight / 2;
    }

    private Extent extentOf(double fx, double fy, double fz) {
        var list = states;
        if (list.isEmpty()) return new Extent(fx, fy, fz, fy, fy);
        double sx = 0;
        double sy = 0;
        double sz = 0;
        double top = Double.NEGATIVE_INFINITY;
        double bottom = Double.POSITIVE_INFINITY;
        for (var state : list) {
            sx += state.x;
            sy += state.y;
            sz += state.z;
            top = Math.max(top, state.y);
            bottom = Math.min(bottom, state.y);
        }
        int count = list.size();
        return new Extent(sx / count, sy / count, sz / count, top, bottom);
    }

    private Shapes.Params shapeParams(int count) {
        double[] stack = new double[count];
        for (int i = 0; i < count; i++) stack[i] = (count - 1 - i) * lineSpacing;
        return new Shapes.Params(layoutRadius, shapeWidth, shapeHeight, shapeTilt, shapeSides, shapeInner, gridColumns, arcAngle, lineSpacing, helixStep, stack);
    }

    private String effectiveLayout() {
        return layoutOverride != null ? layoutOverride : layout;
    }

    @Override
    protected void tick(int interval) {
        ticks += interval;
        runtime.tick(interval, material -> false);
        drainCommands();
        expire();
        var config = plugin.getConfig();
        double dt = interval / TICKS_PER_SECOND;
        double seconds = ticks / TICKS_PER_SECOND;
        double ease = 1 - Math.exp(-config.getDouble(EASE_RATE_KEY, DEFAULT_EASE_RATE) * dt);
        glide(ease);
        var path = pathPoint(seconds);
        var attached = attachedCenter();
        var origin = attached != null ? attached : path;
        double originX = origin == null ? baseX : origin[0];
        double originY = origin == null ? baseY : origin[1];
        double originZ = origin == null ? baseZ : origin[2];
        var nearest = reactive() ? nearest(originX + followX + moveX, originY + moveY, originZ + followZ + moveZ) : null;
        follow(nearest, originX + followX + moveX, originZ + followZ + moveZ, dt);
        face(nearest, originX + followX + moveX, originZ + followZ + moveZ, dt);
        double centerX = originX + followX + moveX;
        double centerY = originY + moveY;
        double centerZ = originZ + followZ + moveZ;
        if (bobHeight > 0) centerY += bobHeight * Math.sin(Math.TAU * bobSpeed * seconds);
        if (orbitRadius > 0) {
            double angle = Math.TAU * orbitSpeed * seconds;
            centerX += orbitRadius * Math.cos(angle);
            centerZ += orbitRadius * Math.sin(angle);
        }
        boolean shaped = !LAYOUT_STACK.equals(effectiveLayout()) && layoutSpeed != 0;
        boolean moving = path != null || attached != null || bobHeight > 0 || orbitRadius > 0 || waveHeight > 0 || shaped
            || facePlayer || followPlayer || ticks <= morphUntil || offsetsMoving();
        double factor = scaleFactor * visibility * proximityFactor(nearest, ease) * burst();
        double spinSpeedNow = spinOverridden ? spinOverride : spinSpeed;
        spinAngle = (spinAngle + spinSpeedNow * dt) % FULL_TURN;
        boolean transforming = spinSpeedNow != 0 || swayAngle > 0 || pulseAmount > 0 || factor != lastFactor;
        lastFactor = factor;
        float spin = (float) Math.toRadians(spinAngle);
        float sway = (float) Math.toRadians(swayAngle * Math.sin(Math.TAU * swaySpeed * seconds));
        float pulse = (float) (factor * (1 + pulseAmount * Math.sin(Math.TAU * pulseSpeed * seconds)));
        List<Packet<?>> packets = new ArrayList<>();
        if (moving) {
            reposition(packets, centerX, centerY, centerZ, seconds, ease);
            extent = extentOf(centerX, centerY, centerZ);
            moveHitbox(packets);
        }
        for (var state : states) update(state, config, transforming, spin, sway, pulse, packets);
        setPosition(centerX, centerY, centerZ);
        broadcast(packets);
        particles(centerX, centerY, centerZ, interval);
    }

    private void drainCommands() {
        Runnable command;
        while ((command = commands.poll()) != null) {
            try {
                command.run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, kind() + ":" + id(), e);
            }
        }
    }

    private void expire() {
        if (spinUntil > 0 && ticks >= spinUntil) {
            spinOverridden = false;
            spinUntil = 0;
        }
        if (scaleUntil > 0 && ticks >= scaleUntil) {
            scaleTarget = 1;
            scaleUntil = 0;
        }
        if (moveUntil > 0 && ticks >= moveUntil) {
            moveTargetX = 0;
            moveTargetY = 0;
            moveTargetZ = 0;
            moveUntil = 0;
        }
        if (hideUntil > 0 && ticks >= hideUntil) setHidden(false, 0);
    }

    private void glide(double ease) {
        moveX = approach(moveX, moveTargetX, ease);
        moveY = approach(moveY, moveTargetY, ease);
        moveZ = approach(moveZ, moveTargetZ, ease);
        scaleFactor = approach(scaleFactor, scaleTarget, ease);
        visibility = approach(visibility, hidden ? 0 : 1, ease);
    }

    private static double approach(double current, double target, double ease) {
        double delta = target - current;
        return Math.abs(delta) < EPSILON ? target : current + delta * ease;
    }

    private boolean offsetsMoving() {
        return moveX != moveTargetX || moveY != moveTargetY || moveZ != moveTargetZ;
    }

    private boolean reactive() {
        return facePlayer || followPlayer || proximityScale > 0;
    }

    private double burst() {
        if (ticks >= pulseEnd) return 1;
        double progress = (ticks - pulseStart) / (double) (pulseEnd - pulseStart);
        return 1 + pulseBurst * Math.sin(Math.PI * progress);
    }

    private double proximityFactor(Nearest nearest, double ease) {
        double target = 1;
        if (proximityScale > 0 && nearest != null && nearest.distance() < proximityRange) {
            target += proximityScale * (1 - nearest.distance() / proximityRange);
        }
        proximity = approach(proximity, target, ease);
        return proximity;
    }

    private Nearest nearest(double cx, double cy, double cz) {
        Eye best = null;
        double bestSquared = Double.MAX_VALUE;
        for (var entry : eyes.entrySet()) {
            if (!isViewer(entry.getKey())) continue;
            var eye = entry.getValue();
            if (!eye.world().equals(worldId)) continue;
            double dx = eye.x() - cx;
            double dy = eye.y() - cy;
            double dz = eye.z() - cz;
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared < bestSquared) {
                bestSquared = squared;
                best = eye;
            }
        }
        return best == null ? null : new Nearest(best, Math.sqrt(bestSquared));
    }

    private void follow(Nearest nearest, double cx, double cz, double dt) {
        if (!followPlayer) return;
        double step = followSpeed * dt;
        if (nearest != null && nearest.distance() <= followRange) {
            double dx = nearest.eye().x() - cx;
            double dz = nearest.eye().z() - cz;
            double distance = Math.hypot(dx, dz);
            if (distance <= followDistance || distance < EPSILON) return;
            double move = Math.min(step, distance - followDistance);
            followX += dx / distance * move;
            followZ += dz / distance * move;
            return;
        }
        double home = Math.hypot(followX, followZ);
        if (home < EPSILON) {
            followX = 0;
            followZ = 0;
            return;
        }
        double move = Math.min(step, home);
        followX -= followX / home * move;
        followZ -= followZ / home * move;
    }

    private void face(Nearest nearest, double cx, double cz, double dt) {
        if (!facePlayer) {
            facing = currentYaw = baseYaw;
            return;
        }
        if (nearest == null) return;
        double dx = nearest.eye().x() - cx;
        double dz = nearest.eye().z() - cz;
        if (Math.abs(dx) < EPSILON && Math.abs(dz) < EPSILON) return;
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double maxStep = plugin.getConfig().getDouble(TURN_SPEED_KEY, DEFAULT_TURN_SPEED) * dt;
        currentYaw += Math.clamp(wrap(target - currentYaw), -maxStep, maxStep);
        facing = currentYaw;
    }

    private static double wrap(double degrees) {
        double d = degrees % FULL_TURN;
        if (d > HALF_TURN) return d - FULL_TURN;
        if (d < -HALF_TURN) return d + FULL_TURN;
        return d;
    }

    private double[] place(Shapes.Params params, String layoutName, HoloLine line, int index, int count, double cx, double cy, double cz, double seconds, double turn) {
        double[] local = Shapes.place(layoutName, params, index, count, turn);
        double lx = local[0] + line.offsetX;
        double ly = local[1] + line.offsetY;
        double lz = local[2] + line.offsetZ;
        if (waveHeight > 0) ly += waveHeight * Math.sin(Math.TAU * waveSpeed * seconds + Math.TAU * index / Math.max(1, count));
        double yawRad = Math.toRadians(currentYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        return new double[]{cx - cos * lx - sin * lz, cy + ly, cz - sin * lx + cos * lz, currentYaw + local[3]};
    }

    private void reposition(List<Packet<?>> packets, double cx, double cy, double cz, double seconds, double ease) {
        var list = states;
        int count = list.size();
        var params = shapeParams(count);
        var layoutName = effectiveLayout();
        double turn = Math.toRadians(layoutSpeed * seconds);
        boolean morphing = ticks < morphUntil;
        for (var state : list) {
            var target = place(params, layoutName, state.line, state.index, count, cx, cy, cz, seconds, turn);
            double x = morphing ? state.x + (target[0] - state.x) * ease : target[0];
            double y = morphing ? state.y + (target[1] - state.y) * ease : target[1];
            double z = morphing ? state.z + (target[2] - state.z) * ease : target[2];
            float yaw = (float) (morphing ? state.yaw + wrap(target[3] - state.yaw) * ease : target[3]);
            if (x == state.x && y == state.y && z == state.z && yaw == state.yaw) continue;
            state.x = x;
            state.y = y;
            state.z = z;
            state.yaw = yaw;
            packets.add(Packets.teleport(state.handle.getId(), x, y, z, yaw, 0f));
        }
    }

    private void moveHitbox(List<Packet<?>> packets) {
        if (hitboxHandle == null) return;
        var bounds = extent;
        double x = bounds.cx();
        double y = hitboxBottom(bounds);
        double z = bounds.cz();
        if (x == hitX && y == hitY && z == hitZ) return;
        hitX = x;
        hitY = y;
        hitZ = z;
        packets.add(Packets.teleport(hitboxHandle.getId(), x, y, z, 0f, 0f));
    }

    private void update(LineState state, FileConfiguration config, boolean transforming, float spin, float sway, float scale, List<Packet<?>> packets) {
        var handle = state.handle;
        if (transforming) state.display.setTransformation(transformation(state.block, state.line, (float) state.line.scale * scale, spin, sway));
        if ((state.dynamic || state.refresh) && state.display instanceof TextDisplay text) {
            state.refresh = false;
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
        if (hitboxHandle != null) {
            list.add(Packets.spawn(hitboxHandle, hitX, hitY, hitZ, 0f, 0f));
            list.add(Packets.fullData(hitboxHandle));
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
        if (hitboxHandle != null) list.add(Packets.teleport(hitboxHandle.getId(), hitX, hitY, hitZ, 0f, 0f));
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
        var size = new Vector3f(scale * (float) line.scaleX, scale * (float) line.scaleY, scale * (float) line.scaleZ);
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
