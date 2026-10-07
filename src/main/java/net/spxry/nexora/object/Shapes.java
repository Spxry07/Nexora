package net.spxry.nexora.object;

import java.util.Locale;
import java.util.function.DoubleFunction;

public final class Shapes {

    public record Params(double radius, double width, double height, double tilt, int sides, double inner, int columns, double arc, double spacing, double helixStep, double[] stackOffsets) {
    }

    private static final String STACK = "STACK";
    private static final String ROW = "ROW";
    private static final String RING = "RING";
    private static final String WHEEL = "WHEEL";
    private static final String HELIX = "HELIX";
    private static final String TORNADO = "TORNADO";
    private static final String SPHERE = "SPHERE";
    private static final String TRIANGLE = "TRIANGLE";
    private static final String SQUARE = "SQUARE";
    private static final String POLYGON = "POLYGON";
    private static final String STAR = "STAR";
    private static final String HEART = "HEART";
    private static final String GRID = "GRID";
    private static final String ARC = "ARC";
    private static final String SPIRAL = "SPIRAL";
    private static final String CUBE = "CUBE";
    private static final String INFINITY = "INFINITY";
    private static final String DIAMOND = "DIAMOND";
    private static final String PYRAMID = "PYRAMID";
    private static final String CROSS = "CROSS";

    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static final double TORNADO_MIN_RADIUS = 0.15;
    private static final double EPSILON = 1e-9;
    private static final double HALF = 0.5;
    private static final double QUARTER_TURN = Math.PI / 2;
    private static final double FULL_DEGREES = 360.0;
    private static final double DEFAULT_ARC = 180.0;
    private static final double DEFAULT_INNER = 0.5;
    private static final double DEFAULT_SPACING = 1.0;
    private static final double FLAT_OUTWARD_MAX_TILT = 45.0;
    private static final double CROSS_ARM = 1.0 / 3.0;
    private static final double DIAMOND_HALF_WIDTH = 0.6;
    private static final double SPIRAL_TURNS = 3.0;
    private static final double HEART_X = 16.0;
    private static final double HEART_Y_SHIFT = 2.5;
    private static final double HEART_Y_SCALE = 16.0;
    private static final double[] HEART_Y_TERMS = {13, -5, -2, -1};
    private static final int CURVE_SAMPLES = 256;
    private static final int MIN_SIDES = 3;
    private static final int AXES = 3;

    private static final double[][] SQUARE_VERTICES = {{-1, 1, 0}, {1, 1, 0}, {1, -1, 0}, {-1, -1, 0}};
    private static final double[][] DIAMOND_VERTICES = {{0, 1, 0}, {DIAMOND_HALF_WIDTH, 0, 0}, {0, -1, 0}, {-DIAMOND_HALF_WIDTH, 0, 0}};
    private static final double[][] CROSS_VERTICES = {
        {CROSS_ARM, 1, 0}, {CROSS_ARM, CROSS_ARM, 0}, {1, CROSS_ARM, 0}, {1, -CROSS_ARM, 0},
        {CROSS_ARM, -CROSS_ARM, 0}, {CROSS_ARM, -1, 0}, {-CROSS_ARM, -1, 0}, {-CROSS_ARM, -CROSS_ARM, 0},
        {-1, -CROSS_ARM, 0}, {-1, CROSS_ARM, 0}, {-CROSS_ARM, CROSS_ARM, 0}, {-CROSS_ARM, 1, 0}
    };
    private static final double[][] HEART_CURVE = closedCurve(Shapes::heartPoint);
    private static final double[][] INFINITY_CURVE = closedCurve(Shapes::infinityPoint);
    private static final double[][] SPIRAL_CURVE = spiralCurve();
    private static final double[][] CUBE_VERTICES = cubeVertices();
    private static final int[] CUBE_EDGES = cubeEdges();
    private static final double[][] PYRAMID_VERTICES = {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}, {0, 1, 0}};
    private static final int[] PYRAMID_EDGES = {0, 1, 1, 2, 2, 3, 3, 0, 0, 4, 1, 4, 2, 4, 3, 4};

    private Shapes() {
    }

    public static double[] place(String layout, Params p, int index, int count, double turnRad) {
        int n = Math.max(1, count);
        int i = Math.max(0, Math.min(index, n - 1));
        String key = layout == null ? STACK : layout.toUpperCase(Locale.ROOT);
        double[] s = {p.radius() * positive(p.width()), p.radius() * positive(p.height()), p.radius()};
        return switch (key) {
            case ROW -> row(p, i, n);
            case RING -> circle(s, 0, fullTurn(i, n) + turnRad);
            case WHEEL -> wheel(s, fullTurn(i, n) + turnRad);
            case HELIX -> circle(s, (n - 1 - i) * p.spacing(), Math.toRadians(p.helixStep()) * i + turnRad);
            case TORNADO -> tornado(p, s, i, n, turnRad);
            case SPHERE -> sphere(s, i, n, turnRad);
            case TRIANGLE -> outline(polygon(MIN_SIDES, 1), true, p, s, i, n, turnRad);
            case SQUARE -> outline(SQUARE_VERTICES, true, p, s, i, n, turnRad);
            case POLYGON -> outline(polygon(Math.max(MIN_SIDES, p.sides()), 1), true, p, s, i, n, turnRad);
            case STAR -> outline(star(Math.max(MIN_SIDES, p.sides()), p.inner() > 0 ? p.inner() : DEFAULT_INNER), true, p, s, i, n, turnRad);
            case HEART -> outline(HEART_CURVE, true, p, s, i, n, turnRad);
            case INFINITY -> outline(INFINITY_CURVE, true, p, s, i, n, turnRad);
            case DIAMOND -> outline(DIAMOND_VERTICES, true, p, s, i, n, turnRad);
            case CROSS -> outline(CROSS_VERTICES, true, p, s, i, n, turnRad);
            case SPIRAL -> outline(SPIRAL_CURVE, false, p, s, i, n, turnRad);
            case ARC -> arc(p, s, i, n, turnRad);
            case GRID -> grid(p, i, n, turnRad);
            case CUBE -> solid(CUBE_VERTICES, CUBE_EDGES, s, i, n, turnRad);
            case PYRAMID -> solid(PYRAMID_VERTICES, PYRAMID_EDGES, s, i, n, turnRad);
            default -> stack(p, i);
        };
    }

    private static double fullTurn(int index, int count) {
        return Math.TAU * index / count;
    }

    private static double positive(double value) {
        return value > 0 ? value : 1.0;
    }

    private static double[] stack(Params p, int index) {
        double[] offsets = p.stackOffsets();
        double y = offsets != null && index < offsets.length ? offsets[index] : 0;
        return new double[]{0, y, 0, 0};
    }

    private static double[] row(Params p, int index, int count) {
        return new double[]{(index - (count - 1) / 2.0) * p.spacing(), 0, 0, 0};
    }

    private static double[] circle(double[] s, double y, double angle) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        return new double[]{-s[0] * dx, y, s[1] * dz, Math.toDegrees(Math.atan2(-dx, dz))};
    }

    private static double[] tornado(Params p, double[] s, int index, int count, double turn) {
        double level = count <= 1 ? 1 : (count - 1 - index) / (double) (count - 1);
        double shrink = Math.max(TORNADO_MIN_RADIUS, level);
        double[] scaled = {s[0] * shrink, s[1] * shrink, s[2] * shrink};
        return circle(scaled, (count - 1 - index) * p.spacing(), Math.toRadians(p.helixStep()) * index + turn);
    }

    private static double[] wheel(double[] s, double angle) {
        return new double[]{s[0] * Math.cos(angle), s[1] * Math.sin(angle), 0, 0};
    }

    private static double[] sphere(double[] s, int index, int count, double turn) {
        double unitY = 1 - 2 * (index + HALF) / count;
        double ring = Math.sqrt(Math.max(0, 1 - unitY * unitY));
        double theta = GOLDEN_ANGLE * index + turn;
        double dx = Math.cos(theta) * ring, dz = Math.sin(theta) * ring;
        return new double[]{-s[0] * dx, s[1] * unitY, s[2] * dz, Math.toDegrees(Math.atan2(-dx, dz))};
    }

    private static double[] outline(double[][] vertices, boolean closed, Params p, double[] s, int index, int count, double turn) {
        double[] point = walk(vertices, null, closed, fraction(closed, index, count), new double[]{s[0], s[1], 0});
        return flat(point[0], point[1], p, turn, true);
    }

    private static double[] arc(Params p, double[] s, int index, int count, double turn) {
        double degrees = p.arc() > 0 ? Math.min(p.arc(), FULL_DEGREES) : DEFAULT_ARC;
        boolean closed = degrees >= FULL_DEGREES;
        double span = Math.toRadians(degrees);
        double angle = closed ? Math.TAU * index / count : (count <= 1 ? 0 : -span / 2 + span * index / (count - 1));
        return flat(s[0] * Math.sin(angle), s[1] * Math.cos(angle), p, turn, true);
    }

    private static double[] grid(Params p, int index, int count, double turn) {
        int columns = Math.max(1, p.columns());
        int rows = (count + columns - 1) / columns;
        int row = index / columns, col = index % columns;
        int inRow = row == rows - 1 ? count - row * columns : columns;
        double spacing = p.spacing() > 0 ? p.spacing() : DEFAULT_SPACING;
        double px = (col - (inRow - 1) / 2.0) * spacing * positive(p.width());
        double py = ((rows - 1) / 2.0 - row) * spacing * positive(p.height());
        return flat(px, py, p, turn, false);
    }

    private static double[] solid(double[][] vertices, int[] edges, double[] s, int index, int count, double turn) {
        double[] point = walk(vertices, edges, true, fraction(true, index, count), s);
        return orient(point[0], point[1], point[2], turn, true);
    }

    private static double fraction(boolean closed, int index, int count) {
        if (closed) return index / (double) count;
        return count <= 1 ? HALF : index / (double) (count - 1);
    }

    private static double[] flat(double px, double py, Params p, double turn, boolean outwardWhenLevel) {
        double tilt = Math.toRadians(p.tilt());
        boolean outward = outwardWhenLevel && Math.abs(p.tilt()) <= FLAT_OUTWARD_MAX_TILT;
        return orient(px, py * Math.sin(tilt), py * Math.cos(tilt), turn, outward);
    }

    private static double[] orient(double x, double y, double z, double turn, boolean outward) {
        double cos = Math.cos(turn), sin = Math.sin(turn);
        double rx = x * cos + z * sin;
        double rz = -x * sin + z * cos;
        double yaw = outward && Math.hypot(rx, rz) > EPSILON ? Math.toDegrees(Math.atan2(rx, rz)) : Math.toDegrees(turn);
        return new double[]{rx, y, rz, yaw};
    }

    private static double[] walk(double[][] v, int[] pairs, boolean closed, double fraction, double[] s) {
        int segments = pairs != null ? pairs.length / 2 : closed ? v.length : v.length - 1;
        double total = 0;
        for (int k = 0; k < segments; k++) total += segmentLength(v, pairs, k, s);
        if (total < EPSILON) return scaled(v[0], s);
        double remaining = Math.max(0, Math.min(1, fraction)) * total;
        for (int k = 0; k < segments; k++) {
            double length = segmentLength(v, pairs, k, s);
            if (remaining <= length || k == segments - 1) {
                double t = length < EPSILON ? 0 : Math.min(1, remaining / length);
                return lerp(v[from(pairs, k)], v[to(v, pairs, k)], t, s);
            }
            remaining -= length;
        }
        return scaled(v[0], s);
    }

    private static int from(int[] pairs, int k) {
        return pairs != null ? pairs[2 * k] : k;
    }

    private static int to(double[][] v, int[] pairs, int k) {
        return pairs != null ? pairs[2 * k + 1] : (k + 1) % v.length;
    }

    private static double segmentLength(double[][] v, int[] pairs, int k, double[] s) {
        double[] a = v[from(pairs, k)], b = v[to(v, pairs, k)];
        double sum = 0;
        for (int axis = 0; axis < AXES; axis++) {
            double d = (b[axis] - a[axis]) * s[axis];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    private static double[] lerp(double[] a, double[] b, double t, double[] s) {
        double[] out = new double[AXES];
        for (int axis = 0; axis < AXES; axis++) out[axis] = (a[axis] + (b[axis] - a[axis]) * t) * s[axis];
        return out;
    }

    private static double[] scaled(double[] v, double[] s) {
        return new double[]{v[0] * s[0], v[1] * s[1], v[2] * s[2]};
    }

    private static double[][] polygon(int sides, double radius) {
        double[][] out = new double[sides][];
        for (int k = 0; k < sides; k++) {
            double angle = QUARTER_TURN + Math.TAU * k / sides;
            out[k] = new double[]{radius * Math.cos(angle), radius * Math.sin(angle), 0};
        }
        return out;
    }

    private static double[][] star(int sides, double inner) {
        double[][] out = new double[sides * 2][];
        for (int k = 0; k < out.length; k++) {
            double radius = k % 2 == 0 ? 1 : inner;
            double angle = QUARTER_TURN + Math.PI * k / sides;
            out[k] = new double[]{radius * Math.cos(angle), radius * Math.sin(angle), 0};
        }
        return out;
    }

    private static double[][] closedCurve(DoubleFunction<double[]> point) {
        double[][] out = new double[CURVE_SAMPLES][];
        for (int k = 0; k < CURVE_SAMPLES; k++) out[k] = point.apply(Math.TAU * k / CURVE_SAMPLES);
        return out;
    }

    private static double[] heartPoint(double t) {
        double x = HEART_X * Math.pow(Math.sin(t), 3);
        double y = 0;
        for (int k = 0; k < HEART_Y_TERMS.length; k++) y += HEART_Y_TERMS[k] * Math.cos((k + 1) * t);
        return new double[]{x / HEART_X, (y + HEART_Y_SHIFT) / HEART_Y_SCALE, 0};
    }

    private static double[] infinityPoint(double t) {
        double denominator = 1 + Math.sin(t) * Math.sin(t);
        return new double[]{Math.cos(t) / denominator, Math.sin(t) * Math.cos(t) / denominator, 0};
    }

    private static double[][] spiralCurve() {
        double[][] out = new double[CURVE_SAMPLES + 1][];
        for (int k = 0; k <= CURVE_SAMPLES; k++) {
            double unit = k / (double) CURVE_SAMPLES;
            double angle = Math.TAU * SPIRAL_TURNS * unit;
            out[k] = new double[]{unit * Math.cos(angle), unit * Math.sin(angle), 0};
        }
        return out;
    }

    private static double[][] cubeVertices() {
        double[][] out = new double[8][];
        for (int k = 0; k < out.length; k++) out[k] = new double[]{(k & 1) * 2 - 1, ((k >> 1) & 1) * 2 - 1, ((k >> 2) & 1) * 2 - 1};
        return out;
    }

    private static int[] cubeEdges() {
        int[] out = new int[24];
        int cursor = 0;
        for (int a = 0; a < 8; a++) {
            for (int bit = 0; bit < AXES; bit++) {
                int b = a ^ (1 << bit);
                if (b > a) {
                    out[cursor++] = a;
                    out[cursor++] = b;
                }
            }
        }
        return out;
    }
}
