package me.tewek.lightengine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Arrays;

/**
 * Debug overlay toggled by {@code /lightengine light_level}.
 * Draws the incoming block-light value as screen-space text pinned to every
 * visible face of every non-air block in a radius around the camera:
 * green = 8+ (safe), yellow = 1-7, red = 0 (hostile mobs can spawn).
 * Extended values above 15 are shown as-is.
 *
 * <p>26.x has no immediate world-space text and gizmo billboards cannot hold
 * still on faces, so labels are projected with the exact vanilla
 * view-projection matrix and drawn upright. The work is split in two:
 * <ul>
 *   <li>SCAN (expensive) — runs only when the camera cell changes or every
 *   few frames: flood-fills sight reachability over the cube around the
 *   camera and caches face labels (position, normal, value, color).</li>
 *   <li>DRAW (cheap) — every frame: re-projects cached labels, culls
 *   backfaces / behind-camera / off-screen, draws text.</li>
 * </ul>
 * Occlusion comes from the flood fill (only faces bordering air reachable
 * from the camera get labels — no x-ray), so it is exact whenever the scan
 * is fresh and costs a fraction of per-label raycasts. No per-frame
 * allocations: all buffers are reused (render thread only).
 */
public final class LightOverlayRenderer {
    private LightOverlayRenderer() {
    }

    private static volatile boolean enabled = false;

    private static final int RADIUS = 16;
    private static final int RADIUS_SQ = RADIUS * RADIUS;
    private static final int DIM = RADIUS * 2 + 1;
    private static final int VOLUME = DIM * DIM * DIM;
    /** Re-scan at most this often (frames) so block edits refresh values. */
    private static final int RESCAN_EVERY_FRAMES = 6;
    /** Faces pointing away from the camera are skipped (dot threshold). */
    private static final double FACING_THRESHOLD = 0.0;
    /** Overlay revision marker, drawn on screen to identify the running build. */
    private static final String OVERLAY_MODE = "ss-flood-13";
    /** Upper bound for cached labels; the scan stops adding past it. */
    private static final int MAX_LABELS = 32768;

    // Scan buffers (render thread only).
    private static final byte[] PASSABLE = new byte[VOLUME];
    private static final byte[] REACHED = new byte[VOLUME];
    private static final int[] QUEUE = new int[VOLUME];
    // Label cache (render thread only).
    private static final double[] LX = new double[MAX_LABELS];
    private static final double[] LY = new double[MAX_LABELS];
    private static final double[] LZ = new double[MAX_LABELS];
    private static final byte[] LNX = new byte[MAX_LABELS];
    private static final byte[] LNY = new byte[MAX_LABELS];
    private static final byte[] LNZ = new byte[MAX_LABELS];
    private static final int[] LVALUE = new int[MAX_LABELS];
    private static int labelCount;
    private static long lastCamCell = Long.MIN_VALUE;
    private static long frameId;
    private static long lastScanFrame = -RESCAN_EVERY_FRAMES;

    public static boolean toggle() {
        enabled = !enabled;
        if (!enabled) {
            labelCount = 0;
            lastCamCell = Long.MIN_VALUE;
        }
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static int index(int dx, int dy, int dz) {
        return ((dx + RADIUS) * DIM + (dy + RADIUS)) * DIM + (dz + RADIUS);
    }

    public static void render(GuiGraphicsExtractor graphics,
                              double camX, double camY, double camZ,
                              int guiWidth, int guiHeight) {
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        frameId++;
        long camCell = (Math.floorDiv((int) Math.floor(camX), 16) & 0xFFFFFFFFL) << 32
                | (Math.floorDiv((int) Math.floor(camZ), 16) & 0xFFFFFFFFL);
        if (camCell != lastCamCell || frameId - lastScanFrame >= RESCAN_EVERY_FRAMES) {
            scan(level, camX, camY, camZ);
            lastCamCell = camCell;
            lastScanFrame = frameId;
        }
        draw(graphics, mc.font, camX, camY, camZ, guiWidth, guiHeight);
    }

    /** Full rescan: passability, flood fill, label collection. */
    private static void scan(ClientLevel level, double camX, double camY, double camZ) {
        labelCount = 0;
        BlockPos center = BlockPos.containing(camX, camY, camZ);
        int baseX = center.getX() - RADIUS;
        int baseY = center.getY() - RADIUS;
        int baseZ = center.getZ() - RADIUS;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    cursor.set(baseX + dx, baseY + dy, baseZ + dz);
                    PASSABLE[index(dx, dy, dz)] =
                            (byte) (level.getBlockState(cursor).isSolidRender() ? 0 : 1);
                }
            }
        }

        Arrays.fill(REACHED, (byte) 0);
        int camDx = Math.clamp((int) Math.floor(camX) - baseX, -RADIUS, RADIUS);
        int camDy = Math.clamp((int) Math.floor(camY) - baseY, -RADIUS, RADIUS);
        int camDz = Math.clamp((int) Math.floor(camZ) - baseZ, -RADIUS, RADIUS);
        int head = 0;
        int tail = 0;
        QUEUE[tail++] = index(camDx, camDy, camDz);
        REACHED[index(camDx, camDy, camDz)] = 1;
        while (head < tail) {
            int code = QUEUE[head++];
            int dz = code % DIM - RADIUS;
            int tmp = code / DIM;
            int dy = tmp % DIM - RADIUS;
            int dx = tmp / DIM - RADIUS;
            if (dx + 1 <= RADIUS && PASSABLE[code + DIM * DIM] != 0 && REACHED[code + DIM * DIM] == 0) {
                REACHED[code + DIM * DIM] = 1;
                QUEUE[tail++] = code + DIM * DIM;
            }
            if (dx - 1 >= -RADIUS && PASSABLE[code - DIM * DIM] != 0 && REACHED[code - DIM * DIM] == 0) {
                REACHED[code - DIM * DIM] = 1;
                QUEUE[tail++] = code - DIM * DIM;
            }
            if (dy + 1 <= RADIUS && PASSABLE[code + DIM] != 0 && REACHED[code + DIM] == 0) {
                REACHED[code + DIM] = 1;
                QUEUE[tail++] = code + DIM;
            }
            if (dy - 1 >= -RADIUS && PASSABLE[code - DIM] != 0 && REACHED[code - DIM] == 0) {
                REACHED[code - DIM] = 1;
                QUEUE[tail++] = code - DIM;
            }
            if (dz + 1 <= RADIUS && PASSABLE[code + 1] != 0 && REACHED[code + 1] == 0) {
                REACHED[code + 1] = 1;
                QUEUE[tail++] = code + 1;
            }
            if (dz - 1 >= -RADIUS && PASSABLE[code - 1] != 0 && REACHED[code - 1] == 0) {
                REACHED[code - 1] = 1;
                QUEUE[tail++] = code - 1;
            }
        }

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > RADIUS_SQ) {
                        continue;
                    }
                    if (PASSABLE[index(dx, dy, dz)] != 0) {
                        continue;
                    }
                    for (Direction dir : Direction.values()) {
                        int nx = dx + dir.getStepX();
                        int ny = dy + dir.getStepY();
                        int nz = dz + dir.getStepZ();
                        if (nx < -RADIUS || nx > RADIUS || ny < -RADIUS || ny > RADIUS || nz < -RADIUS || nz > RADIUS) {
                            continue;
                        }
                        int nidx = index(nx, ny, nz);
                        if (PASSABLE[nidx] == 0 || REACHED[nidx] == 0) {
                            continue;
                        }
                        if (labelCount >= MAX_LABELS) {
                            return;
                        }
                        cursor.set(baseX + nx, baseY + ny, baseZ + nz);
                        int value = level.getBrightness(LightLayer.BLOCK, cursor);
                        int i = labelCount++;
                        LX[i] = baseX + dx + 0.5 + dir.getStepX() * 0.51;
                        LY[i] = baseY + dy + 0.5 + dir.getStepY() * 0.51;
                        LZ[i] = baseZ + dz + 0.5 + dir.getStepZ() * 0.51;
                        LNX[i] = (byte) dir.getStepX();
                        LNY[i] = (byte) dir.getStepY();
                        LNZ[i] = (byte) dir.getStepZ();
                        LVALUE[i] = value;
                    }
                }
            }
        }
    }

    /** Projects cached labels and draws the visible ones. No world access at all. */
    private static void draw(GuiGraphicsExtractor graphics, Font font,
                             double camX, double camY, double camZ,
                             int guiWidth, int guiHeight) {
        if (labelCount == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Matrix4f mvp = mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(new Matrix4f());
        Vector4f clip = new Vector4f();
        // Isolate from whatever pose the HUD left behind: our coordinates are
        // absolute GUI pixels, so draw with an identity pose either way.
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        try {
        graphics.text(font, "LE[" + OVERLAY_MODE + "] labels=" + labelCount, 4, 4, 0xFFFFFFFF);
        for (int i = 0; i < labelCount; i++) {
            double fx = LX[i] - camX;
            double fy = LY[i] - camY;
            double fz = LZ[i] - camZ;
            double distSq = fx * fx + fy * fy + fz * fz;
            if (distSq < 1.0e-6) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            double facing = (LNX[i] * fx + LNY[i] * fy + LNZ[i] * fz) / dist;
            if (facing < FACING_THRESHOLD) {
                continue;
            }
            mvp.transform((float) fx, (float) fy, (float) fz, 1.0f, clip);
            if (clip.w <= 0.001f) {
                continue;
            }
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (ndcX < -1.05f || ndcX > 1.05f || ndcY < -1.05f || ndcY > 1.05f) {
                continue;
            }
            int value = LVALUE[i];
            String text = Integer.toString(value);
            int sx = (int) ((ndcX * 0.5f + 0.5f) * guiWidth);
            int sy = (int) ((1.0f - (ndcY * 0.5f + 0.5f)) * guiHeight);
            int color = value <= 0 ? 0xFFFF5555 : value <= 7 ? 0xFFFFDD55 : 0xFF55FF55;
            graphics.text(font, text, sx - font.width(text) / 2, sy - 4, color);
        }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void announce(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(
                    Component.translatable(on ? "lightengine.overlay.on" : "lightengine.overlay.off"));
        }
    }
}
