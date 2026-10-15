package net.spxry.nexora.object;

import com.mojang.math.Transformation;
import org.bukkit.configuration.file.FileConfiguration;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ShapeMesh {
    record Piece(Vector3f translation, Quaternionf left, Vector3f scale, Quaternionf right, float shade) {}

    private enum Kind { TRIANGLE, RIGHT_TRIANGLE, SQUARE, DIAMOND, POLYGON, CIRCLE, STAR, HEART, CROSS, ARROW, CUBE, PYRAMID, PRISM, OCTAHEDRON, CYLINDER, CONE }

    private record Quad(float width, float height, float originX, float originY) {}

    private static final Set<Kind> SOLIDS = Set.of(Kind.CUBE, Kind.PYRAMID, Kind.PRISM, Kind.OCTAHEDRON, Kind.CYLINDER, Kind.CONE);
    private static final String OUTLINE = "OUTLINE";
    private static final String KEY_MAX_PIECES = "shapes.max-pieces";
    private static final String KEY_MAX_SIDES = "shapes.max-sides";
    private static final String KEY_DOUBLE_SIDED = "shapes.double-sided";
    private static final String KEY_CIRCLE = "shapes.circle-segments";
    private static final String KEY_HEART = "shapes.heart-segments";
    private static final String KEY_CROSS = "shapes.cross-width";
    private static final String KEY_SHAFT = "shapes.arrow-shaft";
    private static final String KEY_HEAD = "shapes.arrow-head";
    private static final String KEY_MIN_THICKNESS = "shapes.min-thickness";
    private static final String KEY_SHADE_TOP = "shapes.shade.top";
    private static final String KEY_SHADE_BOTTOM = "shapes.shade.bottom";
    private static final String KEY_SHADE_X = "shapes.shade.side-x";
    private static final String KEY_SHADE_Z = "shapes.shade.side-z";
    private static final String KEY_QUAD_WIDTH = "shapes.quad.width";
    private static final String KEY_QUAD_HEIGHT = "shapes.quad.height";
    private static final String KEY_QUAD_X = "shapes.quad.origin-x";
    private static final String KEY_QUAD_Y = "shapes.quad.origin-y";
    private static final int MIN_SIDES = 3;
    private static final int RIGHT_SIDES = 4;
    private static final float HALF = 0.5f;
    private static final float FULL_SHADE = 1f;
    private static final float EPSILON = 1.0E-6f;
    private static final float FACING_LIMIT = 0.5f;
    private static final float MIN_INNER = 0.05f;
    private static final float MAX_INNER = 0.95f;
    private static final double QUARTER_TURN = Math.PI / 2;
    private static final double HEART_X = 16;
    private static final double HEART_Y1 = 13;
    private static final double HEART_Y2 = 5;
    private static final double HEART_Y3 = 2;
    private static final double HEART_Y4 = 1;
    private static final int CHANNEL = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;

    private ShapeMesh() {}

    static boolean solid(String shape) {
        return SOLIDS.contains(kindOf(shape));
    }

    static List<Piece> build(FileConfiguration config, HoloLine line) {
        var kind = kindOf(line.shape);
        int sides = Math.clamp(line.shapeSides, MIN_SIDES, Math.max(MIN_SIDES, config.getInt(KEY_MAX_SIDES)));
        var quad = new Quad((float) config.getDouble(KEY_QUAD_WIDTH), (float) config.getDouble(KEY_QUAD_HEIGHT),
            (float) config.getDouble(KEY_QUAD_X), (float) config.getDouble(KEY_QUAD_Y));
        var size = new Vector3f((float) (line.scale * line.scaleX), (float) (line.scale * line.scaleY), (float) (line.scale * line.scaleZ));
        var out = new Builder(quad, size, Math.max(0, config.getInt(KEY_MAX_PIECES)));
        if (SOLIDS.contains(kind)) {
            for (var face : outward(fit(solidFaces(kind, sides)))) {
                out.face(face, shade(config, normal(face)), false);
            }
            return out.pieces;
        }
        var loop = counterClockwise(fit(List.of(loop(config, kind, sides, line))).getFirst());
        boolean twoSided = config.getBoolean(KEY_DOUBLE_SIDED);
        if (OUTLINE.equalsIgnoreCase(line.shapeStyle)) {
            float half = (float) Math.max(config.getDouble(KEY_MIN_THICKNESS), line.shapeThickness) / 2;
            for (int i = 0; i < loop.size(); i++) out.strip(loop.get(i), loop.get((i + 1) % loop.size()), half, twoSided);
            return out.pieces;
        }
        for (var face : fillFaces(config, kind, loop)) out.face(face, FULL_SHADE, twoSided);
        return out.pieces;
    }

    static int shade(int argb, float shade) {
        if (shade >= FULL_SHADE) return argb;
        int r = Math.round(((argb >>> RED_SHIFT) & CHANNEL) * shade);
        int g = Math.round(((argb >>> GREEN_SHIFT) & CHANNEL) * shade);
        int b = Math.round((argb & CHANNEL) * shade);
        return (argb >>> ALPHA_SHIFT) << ALPHA_SHIFT | r << RED_SHIFT | g << GREEN_SHIFT | b;
    }

    private static Kind kindOf(String shape) {
        try {
            return Kind.valueOf(shape.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Kind.TRIANGLE;
        }
    }

    private static final class Builder {
        final List<Piece> pieces = new ArrayList<>();
        final Quad quad;
        final Vector3f size;
        final int limit;

        Builder(Quad quad, Vector3f size, int limit) {
            this.quad = quad;
            this.size = size;
            this.limit = limit;
        }

        void face(List<Vector3f> face, float shade, boolean twoSided) {
            if (face.size() == RIGHT_SIDES && parallelogram(face)) {
                var p0 = face.get(0);
                emit(p0, new Vector3f(face.get(1)).sub(p0), new Vector3f(face.get(3)).sub(p0), shade, twoSided);
                return;
            }
            for (int i = 1; i + 1 < face.size(); i++) triangle(face.get(0), face.get(i), face.get(i + 1), shade, twoSided);
        }

        void triangle(Vector3f a, Vector3f b, Vector3f c, float shade, boolean twoSided) {
            corner(a, b, c, shade, twoSided);
            corner(b, c, a, shade, twoSided);
            corner(c, a, b, shade, twoSided);
        }

        void corner(Vector3f at, Vector3f next, Vector3f previous, float shade, boolean twoSided) {
            var e1 = new Vector3f(next).sub(at).mul(HALF);
            var e2 = new Vector3f(previous).sub(at).mul(HALF);
            emit(at, e1, e2, shade, twoSided);
        }

        void strip(Vector3f a, Vector3f b, float half, boolean twoSided) {
            var along = new Vector3f(b).sub(a);
            if (along.lengthSquared() < EPSILON) return;
            var dir = new Vector3f(along).normalize();
            var side = new Vector3f(-dir.y, dir.x, 0f);
            var p0 = new Vector3f(a).sub(new Vector3f(dir).mul(half)).sub(new Vector3f(side).mul(half));
            var e1 = new Vector3f(along).add(new Vector3f(dir).mul(2 * half));
            var e2 = new Vector3f(side).mul(2 * half);
            emit(p0, e1, e2, FULL_SHADE, twoSided);
        }

        void emit(Vector3f p0, Vector3f e1, Vector3f e2, float shade, boolean twoSided) {
            add(p0, e1, e2, shade);
            if (twoSided) add(p0, e2, e1, shade);
        }

        void add(Vector3f p0, Vector3f e1, Vector3f e2, float shade) {
            if (pieces.size() >= limit) return;
            var normal = new Vector3f(e1).cross(e2);
            if (normal.lengthSquared() < EPSILON * EPSILON) return;
            normal.normalize();
            var a = new Vector3f(e1).div(quad.width());
            var b = new Vector3f(e2).div(quad.height());
            var origin = new Vector3f(p0).sub(new Vector3f(a).mul(quad.originX())).sub(new Vector3f(b).mul(quad.originY()));
            var local = new Matrix4f(new Vector4f(a, 0f), new Vector4f(b, 0f), new Vector4f(normal, 0f), new Vector4f(origin, 1f));
            var matrix = new Matrix4f().scaling(size).mul(local);
            var split = new Transformation(matrix);
            pieces.add(new Piece(new Vector3f(split.getTranslation()), new Quaternionf(split.getLeftRotation()),
                new Vector3f(split.getScale()), new Quaternionf(split.getRightRotation()), shade));
        }
    }

    private static boolean parallelogram(List<Vector3f> face) {
        var diagonal = new Vector3f(face.get(0)).add(face.get(2)).sub(face.get(1)).sub(face.get(3));
        return diagonal.lengthSquared() < EPSILON;
    }

    private static List<Vector3f> loop(FileConfiguration config, Kind kind, int sides, HoloLine line) {
        return switch (kind) {
            case RIGHT_TRIANGLE -> points(-HALF, -HALF, HALF, -HALF, -HALF, HALF);
            case SQUARE -> points(-HALF, -HALF, HALF, -HALF, HALF, HALF, -HALF, HALF);
            case DIAMOND -> points(HALF, 0f, 0f, HALF, -HALF, 0f, 0f, -HALF);
            case POLYGON -> regular(sides, HALF, 0f);
            case CIRCLE -> regular(Math.max(MIN_SIDES, config.getInt(KEY_CIRCLE)), HALF, 0f);
            case STAR -> star(sides, (float) Math.clamp(line.shapeInner, MIN_INNER, MAX_INNER));
            case HEART -> heart(Math.max(MIN_SIDES, config.getInt(KEY_HEART)));
            case CROSS -> cross((float) config.getDouble(KEY_CROSS) / 2);
            case ARROW -> arrow((float) config.getDouble(KEY_SHAFT) / 2, (float) config.getDouble(KEY_HEAD));
            default -> regular(MIN_SIDES, HALF, 0f);
        };
    }

    private static List<List<Vector3f>> fillFaces(FileConfiguration config, Kind kind, List<Vector3f> loop) {
        return switch (kind) {
            case STAR, HEART -> earClip(loop);
            case CROSS -> {
                float w = (float) config.getDouble(KEY_CROSS) / 2;
                yield List.of(rect(-w, -HALF, w, HALF), rect(-HALF, -w, -w, w), rect(w, -w, HALF, w));
            }
            case ARROW -> {
                float s = (float) config.getDouble(KEY_SHAFT) / 2;
                float h = (float) config.getDouble(KEY_HEAD);
                float neck = HALF - h;
                yield List.of(rect(-s, -HALF, s, neck), points(-HALF, neck, HALF, neck, 0f, HALF));
            }
            default -> List.of(loop);
        };
    }

    private static List<Vector3f> points(float... xy) {
        List<Vector3f> out = new ArrayList<>(xy.length / 2);
        for (int i = 0; i + 1 < xy.length; i += 2) out.add(new Vector3f(xy[i], xy[i + 1], 0f));
        return out;
    }

    private static List<Vector3f> rect(float x0, float y0, float x1, float y1) {
        return points(x0, y0, x1, y0, x1, y1, x0, y1);
    }

    private static List<Vector3f> regular(int sides, float radius, float y) {
        List<Vector3f> out = new ArrayList<>(sides);
        for (int i = 0; i < sides; i++) {
            double angle = QUARTER_TURN + Math.TAU * i / sides;
            out.add(new Vector3f((float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle)), y));
        }
        return out;
    }

    private static List<Vector3f> star(int points, float inner) {
        int count = points * 2;
        List<Vector3f> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = QUARTER_TURN + Math.PI * i / points;
            float radius = i % 2 == 0 ? HALF : HALF * inner;
            out.add(new Vector3f((float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle)), 0f));
        }
        return out;
    }

    private static List<Vector3f> heart(int segments) {
        List<Vector3f> out = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double t = Math.TAU * i / segments;
            double sin = Math.sin(t);
            double x = HEART_X * sin * sin * sin;
            double y = HEART_Y1 * Math.cos(t) - HEART_Y2 * Math.cos(2 * t) - HEART_Y3 * Math.cos(3 * t) - HEART_Y4 * Math.cos(4 * t);
            out.add(new Vector3f((float) x, (float) y, 0f));
        }
        return out;
    }

    private static List<Vector3f> cross(float w) {
        return points(-w, -HALF, w, -HALF, w, -w, HALF, -w, HALF, w, w, w, w, HALF, -w, HALF, -w, w, -HALF, w, -HALF, -w, -w, -w);
    }

    private static List<Vector3f> arrow(float s, float h) {
        float neck = HALF - h;
        return points(-s, -HALF, s, -HALF, s, neck, HALF, neck, 0f, HALF, -HALF, neck, -s, neck);
    }

    private static List<Vector3f> counterClockwise(List<Vector3f> loop) {
        double area = 0;
        for (int i = 0; i < loop.size(); i++) {
            var a = loop.get(i);
            var b = loop.get((i + 1) % loop.size());
            area += (double) a.x * b.y - (double) b.x * a.y;
        }
        if (area >= 0) return loop;
        var reversed = new ArrayList<>(loop);
        Collections.reverse(reversed);
        return reversed;
    }

    private static List<List<Vector3f>> earClip(List<Vector3f> loop) {
        List<List<Vector3f>> out = new ArrayList<>();
        List<Vector3f> left = new ArrayList<>(loop);
        int guard = loop.size() * loop.size();
        while (left.size() > MIN_SIDES && guard-- > 0) {
            boolean clipped = false;
            int n = left.size();
            for (int i = 0; i < n && !clipped; i++) {
                var a = left.get((i + n - 1) % n);
                var b = left.get(i);
                var c = left.get((i + 1) % n);
                if (cross(a, b, c) <= EPSILON || containsOther(left, a, b, c)) continue;
                out.add(List.of(a, b, c));
                left.remove(i);
                clipped = true;
            }
            if (!clipped) break;
        }
        if (left.size() == MIN_SIDES) out.add(List.copyOf(left));
        return out;
    }

    private static boolean containsOther(List<Vector3f> loop, Vector3f a, Vector3f b, Vector3f c) {
        for (var p : loop) {
            if (p == a || p == b || p == c) continue;
            if (cross(a, b, p) >= 0 && cross(b, c, p) >= 0 && cross(c, a, p) >= 0) return true;
        }
        return false;
    }

    private static float cross(Vector3f a, Vector3f b, Vector3f c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static List<List<Vector3f>> solidFaces(Kind kind, int sides) {
        List<List<Vector3f>> faces = new ArrayList<>();
        switch (kind) {
            case CUBE -> {
                float[][] c = new float[8][];
                for (int i = 0; i < c.length; i++) c[i] = new float[]{(i & 1) == 0 ? -HALF : HALF, (i & 2) == 0 ? -HALF : HALF, (i & 4) == 0 ? -HALF : HALF};
                int[][] quads = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
                for (var q : quads) faces.add(List.of(vec(c[q[0]]), vec(c[q[1]]), vec(c[q[2]]), vec(c[q[3]])));
            }
            case PYRAMID -> {
                var apex = new Vector3f(0f, HALF, 0f);
                var base = List.of(new Vector3f(-HALF, -HALF, -HALF), new Vector3f(HALF, -HALF, -HALF), new Vector3f(HALF, -HALF, HALF), new Vector3f(-HALF, -HALF, HALF));
                faces.add(base);
                for (int i = 0; i < base.size(); i++) faces.add(List.of(base.get(i), base.get((i + 1) % base.size()), apex));
            }
            case PRISM -> {
                var front = flat(regular(MIN_SIDES, HALF, 0f), HALF);
                var back = flat(regular(MIN_SIDES, HALF, 0f), -HALF);
                faces.add(front);
                faces.add(back);
                for (int i = 0; i < MIN_SIDES; i++) {
                    int j = (i + 1) % MIN_SIDES;
                    faces.add(List.of(front.get(i), front.get(j), back.get(j), back.get(i)));
                }
            }
            case OCTAHEDRON -> {
                var top = new Vector3f(0f, HALF, 0f);
                var bottom = new Vector3f(0f, -HALF, 0f);
                var ring = List.of(new Vector3f(HALF, 0f, 0f), new Vector3f(0f, 0f, HALF), new Vector3f(-HALF, 0f, 0f), new Vector3f(0f, 0f, -HALF));
                for (int i = 0; i < ring.size(); i++) {
                    var a = ring.get(i);
                    var b = ring.get((i + 1) % ring.size());
                    faces.add(List.of(a, b, top));
                    faces.add(List.of(b, a, bottom));
                }
            }
            case CYLINDER, CONE -> {
                var bottom = horizontal(sides, -HALF);
                faces.add(bottom);
                if (kind == Kind.CYLINDER) {
                    var top = horizontal(sides, HALF);
                    faces.add(top);
                    for (int i = 0; i < sides; i++) {
                        int j = (i + 1) % sides;
                        faces.add(List.of(bottom.get(i), bottom.get(j), top.get(j), top.get(i)));
                    }
                } else {
                    var apex = new Vector3f(0f, HALF, 0f);
                    for (int i = 0; i < sides; i++) faces.add(List.of(bottom.get(i), bottom.get((i + 1) % sides), apex));
                }
            }
            default -> {
            }
        }
        return faces;
    }

    private static Vector3f vec(float[] c) {
        return new Vector3f(c[0], c[1], c[2]);
    }

    private static List<Vector3f> flat(List<Vector3f> xy, float z) {
        List<Vector3f> out = new ArrayList<>(xy.size());
        for (var p : xy) out.add(new Vector3f(p.x, p.y, z));
        return out;
    }

    private static List<Vector3f> horizontal(int sides, float y) {
        List<Vector3f> out = new ArrayList<>(sides);
        for (int i = 0; i < sides; i++) {
            double angle = Math.TAU * i / sides;
            out.add(new Vector3f((float) (HALF * Math.cos(angle)), y, (float) (HALF * Math.sin(angle))));
        }
        return out;
    }

    private static List<List<Vector3f>> fit(List<List<Vector3f>> faces) {
        var min = new Vector3f(Float.MAX_VALUE);
        var max = new Vector3f(-Float.MAX_VALUE);
        for (var face : faces) for (var p : face) {
            min.min(p);
            max.max(p);
        }
        var center = new Vector3f(min).add(max).mul(HALF);
        var extent = new Vector3f(max).sub(min);
        float span = Math.max(extent.x, Math.max(extent.y, extent.z));
        float factor = span < EPSILON ? 1f : 1f / span;
        List<List<Vector3f>> out = new ArrayList<>(faces.size());
        for (var face : faces) {
            List<Vector3f> moved = new ArrayList<>(face.size());
            for (var p : face) moved.add(new Vector3f(p).sub(center).mul(factor));
            out.add(moved);
        }
        return out;
    }

    private static List<List<Vector3f>> outward(List<List<Vector3f>> faces) {
        List<List<Vector3f>> out = new ArrayList<>(faces.size());
        for (var face : faces) {
            var centroid = new Vector3f();
            for (var p : face) centroid.add(p);
            centroid.div(face.size());
            if (normal(face).dot(centroid) >= 0) {
                out.add(face);
                continue;
            }
            var reversed = new ArrayList<>(face);
            Collections.reverse(reversed);
            out.add(reversed);
        }
        return out;
    }

    private static Vector3f normal(List<Vector3f> face) {
        var p0 = face.get(0);
        var n = new Vector3f(face.get(1)).sub(p0).cross(new Vector3f(face.get(2)).sub(p0));
        return n.lengthSquared() < EPSILON * EPSILON ? n : n.normalize();
    }

    private static float shade(FileConfiguration config, Vector3f normal) {
        if (normal.y > FACING_LIMIT) return (float) config.getDouble(KEY_SHADE_TOP);
        if (normal.y < -FACING_LIMIT) return (float) config.getDouble(KEY_SHADE_BOTTOM);
        return (float) config.getDouble(Math.abs(normal.x) >= Math.abs(normal.z) ? KEY_SHADE_X : KEY_SHADE_Z);
    }
}
