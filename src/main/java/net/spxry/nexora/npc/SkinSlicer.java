package net.spxry.nexora.npc;

import net.spxry.nexora.npc.SkinParts.Part;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

final class SkinSlicer {

    record Skin(int[] pixels, boolean slim) {}

    private record Rect(int x, int y, int w, int h) {}

    private record Box(int x, int y, int w, int d) {
        Box withWidth(int width) {
            return new Box(x, y, width, d);
        }
    }

    private record Layout(Box base, Box overlay) {}

    static final int SIZE = 64;
    static final int LEGACY_HEIGHT = 32;

    private static final int ALPHA_SHIFT = 24;
    private static final int SLIM_PROBE_X = 54;
    private static final int SLIM_PROBE_Y = 20;
    private static final int HEAD_FACE = 8;
    private static final int HEAD_OVERLAY_X = 32;
    private static final int LIMB_HALF = 6;
    private static final int ARM_WIDTH = 4;
    private static final int SLIM_ARM_WIDTH = 3;

    private static final Box TORSO_BASE = new Box(16, 16, 8, 4);
    private static final Box TORSO_OVERLAY = new Box(16, 32, 8, 4);
    private static final Box RIGHT_ARM_BASE = new Box(40, 16, ARM_WIDTH, 4);
    private static final Box RIGHT_ARM_OVERLAY = new Box(40, 32, ARM_WIDTH, 4);
    private static final Box LEFT_ARM_BASE = new Box(32, 48, ARM_WIDTH, 4);
    private static final Box LEFT_ARM_OVERLAY = new Box(48, 48, ARM_WIDTH, 4);
    private static final Box RIGHT_LEG_BASE = new Box(0, 16, 4, 4);
    private static final Box RIGHT_LEG_OVERLAY = new Box(0, 32, 4, 4);
    private static final Box LEFT_LEG_BASE = new Box(16, 48, 4, 4);
    private static final Box LEFT_LEG_OVERLAY = new Box(0, 48, 4, 4);

    private static final Set<Part> UPPER_PARTS = EnumSet.of(
        Part.TORSO_UPPER, Part.RIGHT_ARM_UPPER, Part.LEFT_ARM_UPPER, Part.RIGHT_LEG_UPPER, Part.LEFT_LEG_UPPER
    );

    private static final int[][] LEGACY_MIRROR_COPIES = {
        {4, 16, 16, 32, 4, 4},
        {8, 16, 16, 32, 4, 4},
        {0, 20, 24, 32, 4, 12},
        {4, 20, 16, 32, 4, 12},
        {8, 20, 8, 32, 4, 12},
        {12, 20, 16, 32, 4, 12},
        {44, 16, -8, 32, 4, 4},
        {48, 16, -8, 32, 4, 4},
        {40, 20, 0, 32, 4, 12},
        {44, 20, -8, 32, 4, 12},
        {48, 20, -16, 32, 4, 12},
        {52, 20, -8, 32, 4, 12}
    };

    private SkinSlicer() {
    }

    static Skin parse(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) throw new IOException("unreadable image");
        int width = image.getWidth();
        int height = image.getHeight();
        if (width != SIZE || (height != SIZE && height != LEGACY_HEIGHT)) {
            throw new IOException("unsupported skin size " + width + "x" + height);
        }
        int[] pixels = new int[SIZE * SIZE];
        image.getRGB(0, 0, width, height, pixels, 0, SIZE);
        if (height == LEGACY_HEIGHT) {
            mirrorLegacyLimbs(pixels);
            return new Skin(pixels, false);
        }
        boolean slim = (pixels[SLIM_PROBE_Y * SIZE + SLIM_PROBE_X] >>> ALPHA_SHIFT) == 0;
        return new Skin(pixels, slim);
    }

    static byte[] encode(int[] pixels) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, SIZE, SIZE, pixels, 0, SIZE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) throw new IOException("png encoder missing");
        return out.toByteArray();
    }

    static int[] slice(Skin skin, Part part) {
        Layout layout = layout(part, skin.slim());
        boolean upper = UPPER_PARTS.contains(part);
        int[] out = new int[SIZE * SIZE];
        paintBox(skin.pixels(), out, layout.base(), 0, upper);
        paintBox(skin.pixels(), out, layout.overlay(), HEAD_OVERLAY_X, upper);
        return out;
    }

    private static void mirrorLegacyLimbs(int[] pixels) {
        for (int[] copy : LEGACY_MIRROR_COPIES) {
            int srcX = copy[0];
            int srcY = copy[1];
            int dstX = srcX + copy[2];
            int dstY = srcY + copy[3];
            int width = copy[4];
            int height = copy[5];
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    pixels[(dstY + j) * SIZE + dstX + (width - 1 - i)] = pixels[(srcY + j) * SIZE + srcX + i];
                }
            }
        }
    }

    private static Layout layout(Part part, boolean slim) {
        int armWidth = slim ? SLIM_ARM_WIDTH : ARM_WIDTH;
        return switch (part) {
            case TORSO_UPPER, TORSO_LOWER -> new Layout(TORSO_BASE, TORSO_OVERLAY);
            case RIGHT_ARM_UPPER, RIGHT_ARM_LOWER ->
                new Layout(RIGHT_ARM_BASE.withWidth(armWidth), RIGHT_ARM_OVERLAY.withWidth(armWidth));
            case LEFT_ARM_UPPER, LEFT_ARM_LOWER ->
                new Layout(LEFT_ARM_BASE.withWidth(armWidth), LEFT_ARM_OVERLAY.withWidth(armWidth));
            case RIGHT_LEG_UPPER, RIGHT_LEG_LOWER -> new Layout(RIGHT_LEG_BASE, RIGHT_LEG_OVERLAY);
            case LEFT_LEG_UPPER, LEFT_LEG_LOWER -> new Layout(LEFT_LEG_BASE, LEFT_LEG_OVERLAY);
            case HEAD -> throw new IllegalArgumentException("head is not sliced");
        };
    }

    private static void paintBox(int[] src, int[] dst, Box box, int headX, boolean upper) {
        int sideY = box.y() + box.d() + (upper ? 0 : LIMB_HALF);
        int frontX = box.x() + box.d();
        int frontY = box.y() + box.d();
        Rect topFace = upper
            ? new Rect(frontX, box.y(), box.w(), box.d())
            : new Rect(frontX, frontY + LIMB_HALF - 1, box.w(), 1);
        Rect bottomFace = upper
            ? new Rect(frontX, frontY + LIMB_HALF, box.w(), 1)
            : new Rect(frontX + box.w(), box.y(), box.w(), box.d());
        Rect rightFace = new Rect(box.x(), sideY, box.d(), LIMB_HALF);
        Rect frontFace = new Rect(frontX, sideY, box.w(), LIMB_HALF);
        Rect leftFace = new Rect(frontX + box.w(), sideY, box.d(), LIMB_HALF);
        Rect backFace = new Rect(frontX + box.w() + box.d(), sideY, box.w(), LIMB_HALF);
        resize(src, topFace, dst, headX + HEAD_FACE, 0);
        resize(src, bottomFace, dst, headX + HEAD_FACE * 2, 0);
        resize(src, rightFace, dst, headX, HEAD_FACE);
        resize(src, frontFace, dst, headX + HEAD_FACE, HEAD_FACE);
        resize(src, leftFace, dst, headX + HEAD_FACE * 2, HEAD_FACE);
        resize(src, backFace, dst, headX + HEAD_FACE * 3, HEAD_FACE);
    }

    private static void resize(int[] src, Rect from, int[] dst, int dstX, int dstY) {
        for (int j = 0; j < HEAD_FACE; j++) {
            int srcRow = from.y() + j * from.h() / HEAD_FACE;
            for (int i = 0; i < HEAD_FACE; i++) {
                int srcColumn = from.x() + i * from.w() / HEAD_FACE;
                dst[(dstY + j) * SIZE + dstX + i] = src[srcRow * SIZE + srcColumn];
            }
        }
    }
}
