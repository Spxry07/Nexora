package net.spxry.nexora.object;

import org.bukkit.Material;
import org.bukkit.entity.Pose;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class Keyframes {
    static final int HEAD = 0;
    static final int BODY = 1;
    static final int LEFT_ARM = 2;
    static final int RIGHT_ARM = 3;
    static final int LEFT_LEG = 4;
    static final int RIGHT_LEG = 5;
    static final int LIMBS = 6;
    static final int PITCH = 0;
    static final int YAW = 1;
    static final int AXES = 3;
    static final int SIZE = LIMBS * AXES;

    enum Hand { NONE, MAIN, OFF }

    record State(Pose pose, Hand use, Material item, Material offItem) {
        static final State NONE = new State(null, Hand.NONE, null, null);
    }

    static final Keyframes EMPTY = new Keyframes(new float[0][], new int[0], new State[0], new Hand[0]);

    private static final int MIN_TICKS = 1;
    private static final int MAX_TICKS = 1200;
    private static final int MAX_FRAMES = 256;
    private static final String FIELD_SEPARATOR = "=";
    private static final String AXIS_SEPARATOR = ",";
    private static final String LINE_SPLIT = "\\R";
    private static final String TOKEN_SPLIT = "\\s+";
    private static final String VALUE_NONE = "none";
    private static final String HAND_MAIN = "main";
    private static final String HAND_OFF = "off";
    private static final String KEY_POSE = "pose";
    private static final String KEY_USE = "use";
    private static final String KEY_ITEM = "item";
    private static final String KEY_OFF_ITEM = "offitem";
    private static final String KEY_SWING = "swing";
    private static final Set<String> POSES = Set.of("STANDING", "SNEAKING", "SWIMMING", "FALL_FLYING", "SPIN_ATTACK", "SLEEPING", "SITTING");
    private static final List<String> NAMES = List.of("head", "body", "larm", "rarm", "lleg", "rleg");
    private static final float[] DEFAULTS = {0F, 0F, 0F, 0F, 0F, 0F, -10F, 0F, -10F, -15F, 0F, 10F, -1F, 0F, -1F, 1F, 0F, 1F};

    private final float[][] poses;
    private final int[] starts;
    private final int[] durations;
    private final State[] states;
    private final Hand[] swings;
    private final int total;

    private Keyframes(float[][] poses, int[] durations, State[] states, Hand[] swings) {
        this.poses = poses;
        this.durations = durations;
        this.states = states;
        this.swings = swings;
        this.starts = new int[durations.length];
        int sum = 0;
        for (int i = 0; i < durations.length; i++) {
            starts[i] = sum;
            sum += durations[i];
        }
        this.total = sum;
    }

    static int at(int limb, int axis) {
        return limb * AXES + axis;
    }

    boolean isEmpty() {
        return poses.length == 0;
    }

    State state(int frame) {
        return states[frame];
    }

    Hand swing(int frame) {
        return swings[frame];
    }

    static Keyframes parse(String text) {
        if (text == null || text.isBlank()) return EMPTY;
        List<float[]> frames = new ArrayList<>();
        List<Integer> ticks = new ArrayList<>();
        List<State> states = new ArrayList<>();
        List<Hand> swings = new ArrayList<>();
        float[] previous = DEFAULTS;
        State carried = State.NONE;
        for (var line : text.split(LINE_SPLIT)) {
            if (frames.size() >= MAX_FRAMES) break;
            var tokens = line.trim().split(TOKEN_SPLIT);
            if (tokens[0].isEmpty()) continue;
            int duration;
            try {
                duration = Math.clamp(Integer.parseInt(tokens[0]), MIN_TICKS, MAX_TICKS);
            } catch (NumberFormatException e) {
                continue;
            }
            float[] pose = previous.clone();
            State state = carried;
            Hand swing = Hand.NONE;
            for (int i = 1; i < tokens.length; i++) {
                int split = tokens[i].indexOf(FIELD_SEPARATOR);
                if (split <= 0) continue;
                var key = tokens[i].substring(0, split).toLowerCase(Locale.ROOT);
                var value = tokens[i].substring(split + 1).trim();
                switch (key) {
                    case KEY_POSE -> state = new State(readPose(value, state.pose()), state.use(), state.item(), state.offItem());
                    case KEY_USE -> state = new State(state.pose(), readHand(value, state.use()), state.item(), state.offItem());
                    case KEY_ITEM -> state = new State(state.pose(), state.use(), readMaterial(value, state.item()), state.offItem());
                    case KEY_OFF_ITEM -> state = new State(state.pose(), state.use(), state.item(), readMaterial(value, state.offItem()));
                    case KEY_SWING -> swing = readHand(value, Hand.NONE);
                    default -> readLimb(key, value, pose);
                }
            }
            frames.add(pose);
            ticks.add(duration);
            states.add(state);
            swings.add(swing);
            previous = pose;
            carried = state;
        }
        if (frames.isEmpty()) return EMPTY;
        return new Keyframes(frames.toArray(new float[0][]), ticks.stream().mapToInt(Integer::intValue).toArray(),
            states.toArray(new State[0]), swings.toArray(new Hand[0]));
    }

    private static Pose readPose(String value, Pose fallback) {
        var name = value.toUpperCase(Locale.ROOT);
        if (!POSES.contains(name)) return fallback;
        try {
            return Pose.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Hand readHand(String value, Hand fallback) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case HAND_MAIN -> Hand.MAIN;
            case HAND_OFF -> Hand.OFF;
            case VALUE_NONE -> Hand.NONE;
            default -> fallback;
        };
    }

    private static Material readMaterial(String value, Material fallback) {
        if (value.equalsIgnoreCase(VALUE_NONE)) return null;
        var material = Material.matchMaterial(value);
        if (material == null || !material.isItem()) return fallback;
        return material.isAir() ? null : material;
    }

    private static void readLimb(String name, String value, float[] pose) {
        int limb = NAMES.indexOf(name);
        if (limb < 0) return;
        var parts = value.split(AXIS_SEPARATOR, -1);
        if (parts.length != AXES) return;
        float[] values = new float[AXES];
        try {
            for (int axis = 0; axis < AXES; axis++) {
                values[axis] = Float.parseFloat(parts[axis].trim());
                if (!Float.isFinite(values[axis])) return;
            }
        } catch (NumberFormatException e) {
            return;
        }
        System.arraycopy(values, 0, pose, at(limb, PITCH), AXES);
    }

    int sample(double elapsed, float[] out) {
        double t = elapsed % total;
        if (t < 0) t += total;
        int found = Arrays.binarySearch(starts, (int) t);
        int index = found >= 0 ? found : -found - 2;
        float progress = (float) ((t - starts[index]) / durations[index]);
        float[] from = poses[index];
        float[] to = poses[(index + 1) % poses.length];
        for (int i = 0; i < SIZE; i++) out[i] = from[i] + (to[i] - from[i]) * progress;
        return index;
    }
}
