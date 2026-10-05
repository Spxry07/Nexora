package net.spxry.nexora.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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

    static final Keyframes EMPTY = new Keyframes(new float[0][], new int[0]);

    private static final int MIN_TICKS = 1;
    private static final int MAX_TICKS = 1200;
    private static final int MAX_FRAMES = 256;
    private static final String FIELD_SEPARATOR = "=";
    private static final String AXIS_SEPARATOR = ",";
    private static final String LINE_SPLIT = "\\R";
    private static final String TOKEN_SPLIT = "\\s+";
    private static final List<String> NAMES = List.of("head", "body", "larm", "rarm", "lleg", "rleg");
    private static final float[] DEFAULTS = {0F, 0F, 0F, 0F, 0F, 0F, -10F, 0F, -10F, -15F, 0F, 10F, -1F, 0F, -1F, 1F, 0F, 1F};

    private final float[][] poses;
    private final int[] starts;
    private final int[] durations;
    private final int total;

    private Keyframes(float[][] poses, int[] durations) {
        this.poses = poses;
        this.durations = durations;
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

    static Keyframes parse(String text) {
        if (text == null || text.isBlank()) return EMPTY;
        List<float[]> frames = new ArrayList<>();
        List<Integer> ticks = new ArrayList<>();
        float[] previous = DEFAULTS;
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
            for (int i = 1; i < tokens.length; i++) readLimb(tokens[i], pose);
            frames.add(pose);
            ticks.add(duration);
            previous = pose;
        }
        if (frames.isEmpty()) return EMPTY;
        return new Keyframes(frames.toArray(new float[0][]), ticks.stream().mapToInt(Integer::intValue).toArray());
    }

    private static void readLimb(String token, float[] pose) {
        int split = token.indexOf(FIELD_SEPARATOR);
        if (split <= 0) return;
        int limb = NAMES.indexOf(token.substring(0, split).toLowerCase(Locale.ROOT));
        if (limb < 0) return;
        var parts = token.substring(split + 1).split(AXIS_SEPARATOR, -1);
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

    void sample(double elapsed, float[] out) {
        double t = elapsed % total;
        if (t < 0) t += total;
        int found = Arrays.binarySearch(starts, (int) t);
        int index = found >= 0 ? found : -found - 2;
        float progress = (float) ((t - starts[index]) / durations[index]);
        float[] from = poses[index];
        float[] to = poses[(index + 1) % poses.length];
        for (int i = 0; i < SIZE; i++) out[i] = from[i] + (to[i] - from[i]) * progress;
    }
}
