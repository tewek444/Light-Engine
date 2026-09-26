package me.tewek.lightengine.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 2.5D isometric night-preview diorama: a flat grass field seen from the top
 * corner, with the selected block's item icon floating on the center pedestal.
 *
 * <p>Layout mirrors alpha3 ({@code FIELD_Y = 2} grass top with a two-block
 * stone body, selected block on the center cell {@code x = z = 1}, field
 * {@code (2H+1)^2} with {@code H = max(radius, 1)}), but every visual is drawn
 * with 2D primitives only ({@code fill}/{@code drawString}/
 * {@code drawCenteredString}/{@code renderItem}).
 *
 * <p>Kept 1:1 from the 26.3 scene: camera constants, zoom/pan math,
 * grow/shrink animation state machine, camera fit + ease, level arithmetic
 * ({@code max(0, R - manhattan)} flood-fill falloff, one extra step down from
 * the pedestal), overlay thresholds, shade alpha, label stride, caption,
 * legend, {@code savedRadius}/{@code avoid} handling.
 *
 * <p>Simplified honestly: blocks are flat isometric diamonds (grass tops,
 * stone rim skirts, glass pedestal cube) instead of textured voxels; the
 * per-pixel top-face shade pass is one translucent black diamond per cell;
 * the pedestal shows the block's 2D item sprite (or a glass diamond when the
 * block has no item) instead of a rendered voxel.
 *
 * <p>Forge 1.20.1 notes: draws through {@link GuiGraphics} (no strata, no
 * render pipelines), the baked field goes through a {@link DynamicTexture}
 * blit, and all clamps avoid {@code Math.clamp} (Java 17 has none).
 *
 * @param savedRadius saved (registry) emission, shown when preview differs
 * @param avoid popup rect {x, y, w, h} numbers must not cover, or null
 */
public final class EditorScene {
    private EditorScene() {
    }

    /** Grass field level (top), with a two-block stone body below. */
    private static final int FIELD_Y = 2;
    /** Pedestal cell of the selected block (field center, one above). */
    private static final int PEDESTAL_X = 1;
    private static final int PEDESTAL_Z = 1;
    private static final int PEDESTAL_Y = FIELD_Y + 1;
    /** World-Y the camera centers on: field bottom (0) .. pedestal top (4). */
    private static final int ANCHOR_Y = (FIELD_Y - 2 + PEDESTAL_Y + 1) / 2;

    /**
     * Corner-camera constants inherited from alpha3. The yaw (-45) selects the
     * ground diagonal and the pitch (-30) the 2:1 foreshorten used below
     * (tile width : height = 2:1).
     */
    private static final float CAMERA_YAW = -45.0F;
    private static final float CAMERA_PITCH = -30.0F;

    private static final int PAD = 6;

    /** User zoom (ctrl+wheel), centered on the cursor. */
    private static float sceneZoom = 1.0F;
    private static float sceneOx = 0.0F;
    private static float sceneOy = 0.0F;
    private static final float MIN_ZOOM = 0.25F;
    private static final float MAX_ZOOM = 8.0F;

    private static float baseCenterX(int x, int w) {
        return x + w / 2.0F;
    }

    private static float baseCenterY(int y, int h) {
        return y + PAD + (h - PAD * 2 - 10) * 0.52F;
    }

    /**
     * Ctrl+wheel zoom, keeping the world point under the cursor stable.
     * Call only when the cursor is inside the scene rect.
     */
    public static void zoomAt(double mouseX, double mouseY, double amount, int x, int y, int w, int h) {
        if (amount == 0.0) {
            return;
        }
        float factor = (float) Math.pow(1.15, amount);
        float next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, sceneZoom * factor));
        float k = next / sceneZoom;
        float bx = baseCenterX(x, w);
        float by = baseCenterY(y, h);
        sceneOx = (float) (mouseX - bx - (mouseX - bx - sceneOx) * k);
        sceneOy = (float) (mouseY - by - (mouseY - by - sceneOy) * k);
        sceneOx = Math.min(w, Math.max(-w, sceneOx));
        sceneOy = Math.min(h, Math.max(-h, sceneOy));
        sceneZoom = next;
    }

    /** Ctrl+drag pan in screen pixels. */
    public static void panBy(double dx, double dy, int w, int h) {
        sceneOx = Math.min(w, Math.max(-w, (float) (sceneOx + dx)));
        sceneOy = Math.min(h, Math.max(-h, (float) (sceneOy + dy)));
    }

    /** Grow/shrink animation (settings toggle), off by default. */
    private static boolean animEnabled;
    private static int animFromHalf = -1;
    private static int animToHalf = -1;
    /** Latest requested target while one is still flying; -1 = none. */
    private static int animQueuedHalf = -1;
    private static double animT0;
    private static final double ANIM_DUR = 0.45;
    private static final double ANIM_SHRINK_DUR = 0.3;
    private static final double ANIM_STAGGER = 0.035;
    private static final double ANIM_STAGGER_CAP = 0.5;
    private static final double ANIM_JITTER = 0.15;
    private static final double ANIM_DROP = 7.0;
    private static final double ANIM_SINK = 3.0;

    /** Smoothed camera fit-scale (never jumps, eases toward the target). */
    private static float smoothScale;
    private static double lastFrameT = -1.0;
    private static final double CAMERA_EASE = 7.0;

    public static boolean isAnimationEnabled() {
        return animEnabled;
    }

    public static void setAnimationEnabled(boolean enabled) {
        animEnabled = enabled;
    }

    private static int chebDist(int gx, int gz) {
        return Math.max(Math.abs(gx - PEDESTAL_X), Math.abs(gz - PEDESTAL_Z));
    }

    /** Stable per-cell jitter in [0, 1): neighbors never fly in lockstep. */
    private static double cellJitter(int gx, int gz) {
        long h = (long) gx * 73856093L ^ (long) gz * 19349663L;
        h ^= h >>> 13;
        h *= 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFFFFL) / (double) 0x100000000L;
    }

    /** When the active flight fully settles (rings + jitter + duration). */
    private static double animEnd() {
        double dur = animToHalf > animFromHalf ? ANIM_DUR : ANIM_SHRINK_DUR;
        return animT0 + ANIM_STAGGER_CAP + ANIM_JITTER + dur;
    }

    /** Y offset (in blocks) of a live field cell this frame (0 when settled). */
    private static float growOffset(int gx, int gz, double now) {
        if (!animEnabled || animToHalf < 0 || animToHalf <= animFromHalf) {
            return 0.0F;
        }
        int cheb = chebDist(gx, gz);
        if (cheb <= animFromHalf || cheb > animToHalf) {
            return 0.0F;
        }
        double delay = Math.min((cheb - animFromHalf - 1) * ANIM_STAGGER, ANIM_STAGGER_CAP)
                + cellJitter(gx, gz) * ANIM_JITTER;
        double p = (now - animT0 - delay) / ANIM_DUR;
        if (p >= 1.0) {
            return 0.0F;
        }
        if (p <= 0.0) {
            return (float) -ANIM_DROP;
        }
        double eased = 1.0 - Math.pow(1.0 - p, 3.0);
        return (float) (-ANIM_DROP * (1.0 - eased));
    }

    // ----- flat-fill palette -----

    private static final int GRASS_TOP = 0xFF74B34A;
    private static final int STONE_LEFT = 0xFF76767C;
    private static final int STONE_RIGHT = 0xFF606066;

    /** Nominal tile size, kept for API compatibility. */
    public static final int GRID = 16;

    /**
     * World-like night level of a surface cell: flood-fill falloff on open
     * ground, one step per block plus the step down from the pedestal.
     */
    public static int valueAt(int radius, int gx, int gz) {
        return Math.max(0, surfaceLevel(radius, gx, gz));
    }

    public static int colorFor(int value) {
        return colorForLevel(value);
    }

    /**
     * Block-light nibble for one cell, always the world-like night falloff
     * (block level clamped to the lightmap range like in the world). Callers
     * that draw keep the raw 0..255 value separately.
     */
    private static int packedFor(int level) {
        return Math.min(15, Math.max(0, level));
    }

    /** Same thresholds as the in-game light overlay command (opaque). */
    private static int colorForLevel(int value) {
        return value <= 0 ? 0xFFFF5555 : value <= 7 ? 0xFFFFDD55 : 0xFF55FF55;
    }

    /** Surface level before clamping (may exceed 15; text shows it raw). */
    private static int surfaceLevel(int radius, int gx, int gz) {
        int dist = Math.abs(gx - PEDESTAL_X) + Math.abs(gz - PEDESTAL_Z);
        return radius - dist - (PEDESTAL_Y - FIELD_Y);
    }

    /** Reused buffers for the level-number pass (surface cells only). */
    private static float[] numX = new float[0];
    private static float[] numY = new float[0];
    private static int[] numV = new int[0];
    private static int numCount;
    /** Scratch pair for the projection (same reuse pattern as alpha3). */
    private static final float[] PROBE = new float[2];

    // ----- baked field image (perf): one blit per frame instead of ~20k fills.
    //
    // Submitting every diamond/wall as its own draw state costs ~20k calls
    // per frame at radius 15 (0 FPS). The field (frame excluded) is therefore
    // rasterized once into a DynamicTexture on the CPU whenever its inputs
    // change (rect, radius, zoom, pan, fit-scale, animation frame) and drawn
    // with a single blit per frame. Everything is opaque and painter-ordered,
    // so pixels are plain overwrites. Level numbers, the item icon, caption
    // and legend stay live (cheap, and text can't bake).
    private static final ResourceLocation IMG_ID =
            ResourceLocation.fromNamespaceAndPath("lightengine", "gui/scene_field");
    private static NativeImage bakeImg;
    private static DynamicTexture bakeTex;
    private static boolean bakeRegistered;
    // Last baked inputs; animation forces a re-bake every frame while flying.
    private static int bakeX, bakeY, bakeW, bakeH, bakeRadius;
    private static float bakeZoom, bakeOx, bakeOy, bakeScale;

    private static void ensureBake(int w, int h) {
        if (bakeImg != null && (bakeImg.getWidth() != w || bakeImg.getHeight() != h)) {
            bakeImg.close();
            bakeImg = null;
        }
        if (bakeImg == null) {
            bakeImg = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            if (bakeTex != null) {
                bakeTex.setPixels(bakeImg);
            } else {
                bakeTex = new DynamicTexture(bakeImg);
            }
            if (!bakeRegistered) {
                Minecraft.getInstance().getTextureManager().register(IMG_ID, bakeTex);
                bakeRegistered = true;
            }
        }
    }

    /** Swap R and B channels (ABGR int <-> RGBA int, alpha untouched). */
    private static int abgrToRgba(int c) {
        return (c & 0xFF000000) | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
    }

    /**
     * Opaque pre-darkened grass for the shade pass. The old code drew black
     * with alpha {@code shadeA = (1 - min(v,15)/15) * SHADE_MAX} over grass;
     * on the opaque frame that equals grass scaled by {@code 1 - shadeA},
     * and opaque avoids double-darkening where cells overlap mid-animation.
     */
    private static int shadedTopAbgr(int level) {
        float k = 0.5F + Math.min(level, 15) / 30.0F;
        int r = (int) ((GRASS_TOP >> 16 & 0xFF) * k);
        int g = (int) ((GRASS_TOP >> 8 & 0xFF) * k);
        int b = (int) ((GRASS_TOP & 0xFF) * k);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    // ----- 2D isometric projection -----

    /** Current frame camera: screen center, pixel scale, tile half-axes, level height. */
    private static float viewCx;
    private static float viewCy;
    private static float viewEff;
    private static float viewHw;
    private static float viewHh;
    private static float viewLh;

    /**
     * Projects a block-top point to screen pixels. Yaw -45 puts
     * {@code +x} right-down and {@code +z} left-down; pitch -30 gives the 2:1
     * foreshorten ({@code viewHw = 2 * viewHh}). Returns a reused scratch
     * pair; copy the values before the next call.
     */
    private static float[] worldToScreen(int gx, int yTop, int gz) {
        PROBE[0] = viewCx + ((gx - PEDESTAL_X) - (gz - PEDESTAL_Z)) * viewHw;
        PROBE[1] = viewCy + ((gx - PEDESTAL_X) + (gz - PEDESTAL_Z)) * viewHh
                - (yTop - ANCHOR_Y) * viewLh;
        return PROBE;
    }

    public static void render(GuiGraphics graphics, int x, int y, int w, int h,
                              Block center, int radius, boolean showLevels) {
        render(graphics, x, y, w, h, center, radius, radius, showLevels, null);
    }

    public static void render(GuiGraphics graphics, int x, int y, int w, int h,
                              Block center, int radius, int savedRadius, boolean showLevels, int[] avoid) {
        LeTheme.frame(graphics, x, y, w, h, 0xFF101014, LeTheme.PANEL_BORDER);

        int innerX = x + PAD;
        int innerY = y + PAD;
        int innerW = w - PAD * 2;
        int innerH = h - PAD * 2 - 10;
        if (innerW < 60 || innerH < 60) {
            return;
        }

        int half = Math.max(radius, 1);
        int extent = 2 * half + 1;
        double now = System.nanoTime() / 1_000_000_000.0;
        if (!animEnabled) {
            animFromHalf = animToHalf = half;
            animQueuedHalf = -1;
        } else if (animToHalf < 0) {
            animFromHalf = animToHalf = half;
            animQueuedHalf = -1;
            animT0 = now;
        } else {
            if (now >= animEnd()) {
                // Active flight settled: adopt the queued target, if any.
                animFromHalf = animToHalf;
                if (animQueuedHalf >= 0 && animQueuedHalf != animToHalf) {
                    animToHalf = animQueuedHalf;
                    animT0 = now;
                }
                animQueuedHalf = -1;
            }
            if (half != animToHalf) {
                if (animFromHalf == animToHalf) {
                    // Idle: take off immediately.
                    animFromHalf = animToHalf;
                    animToHalf = half;
                    animT0 = now;
                    animQueuedHalf = -1;
                } else {
                    // Busy: remember the latest target, active flight goes on.
                    animQueuedHalf = half;
                }
            }
        }
        // Fit the rotated field: width grows ~3 units per half-step,
        // height ~1 (plus the pedestal block on small fields).
        // The camera eases toward the fit instead of jumping (slider drags!).
        float fitScale = Math.min(innerW / (3.0F * half + 2.5F), innerH / (1.0F * half + 4.0F));
        double dt = lastFrameT < 0.0 ? 0.0 : Math.min(now - lastFrameT, 0.05);
        lastFrameT = now;
        if (smoothScale <= 0.0F) {
            smoothScale = fitScale;
        } else if (dt > 0.0) {
            smoothScale += (fitScale - smoothScale) * (float) (1.0 - Math.exp(-dt * CAMERA_EASE));
        }
        // Lateral zoom on the single 2D plane (no depth range to protect).
        float eff = smoothScale * sceneZoom;
        if (eff <= 0.0F) {
            return;
        }
        viewCx = baseCenterX(x, w) + sceneOx;
        viewCy = baseCenterY(y, h) + sceneOy;
        viewEff = eff;
        viewHw = 0.75F * eff;
        viewHh = 0.375F * eff;
        viewLh = 0.75F * eff;

        int total = extent * extent;
        // Label density follows the screen, not the field: zooming in
        // restores every-cell labels, zooming out thins them. The stride is
        // also bumped until at most a few hundred labels remain, so huge
        // radii can't flood the frame with text.
        float pitchPx = (float) Math.sqrt(viewHw * viewHw + viewHh * viewHh);
        int stride = Math.max(1, (int) Math.ceil(9.0F / Math.max(pitchPx, 0.05F)));
        int spanN = (extent + stride - 1) / stride;
        while (spanN * spanN > 800 && stride < extent) {
            stride++;
            spanN = (extent + stride - 1) / stride;
        }
        if (numX.length < total) {
            numX = new float[total];
            numY = new float[total];
            numV = new int[total];
        }
        numCount = 0;

        int lo = PEDESTAL_X - half;
        int hi = PEDESTAL_X + half;
        if (showLevels) {
            // Only stride cells are anchored (the draw pass shows them all).
            for (int gx = lo; gx <= hi; gx++) {
                if ((gx - lo) % stride != 0) {
                    continue;
                }
                for (int gz = lo; gz <= hi; gz++) {
                    if ((gz - lo) % stride != 0) {
                        continue;
                    }
                    float[] p = worldToScreen(gx, FIELD_Y + 1, gz);
                    numX[numCount] = p[0];
                    numY[numCount] = p[1] - growOffset(gx, gz, now) * viewLh;
                    numV[numCount] = Math.max(0, surfaceLevel(radius, gx, gz));
                    numCount++;
                }
            }
        }

        int clipX0 = x + 1;
        int clipY0 = y + 1;
        int clipX1 = x + w - 1;
        int clipY1 = y + h - 1;
        // The field is baked for the whole clip rect (up to the visible
        // frame), exactly like the live numbers below — never just the
        // inner layout rect.
        int clipW = clipX1 - clipX0;
        int clipH = clipY1 - clipY0;

        boolean flying = animEnabled && animToHalf >= 0 && now < animEnd();
        boolean dirty = bakeImg == null
                || bakeW != clipW || bakeH != clipH
                || bakeX != clipX0 || bakeY != clipY0 || bakeRadius != radius
                || bakeZoom != sceneZoom || bakeOx != sceneOx || bakeOy != sceneOy
                || bakeScale != smoothScale || flying;
        if (dirty) {
            ensureBake(clipW, clipH);
            bakeX = clipX0;
            bakeY = clipY0;
            bakeW = clipW;
            bakeH = clipH;
            bakeRadius = radius;
            bakeZoom = sceneZoom;
            bakeOx = sceneOx;
            bakeOy = sceneOy;
            bakeScale = smoothScale;
            bakeImg.fillRect(0, 0, clipW, clipH, 0);
            bakeField(radius, now, lo, hi, clipX0, clipY0, clipX1, clipY1);
            bakeTex.upload();
        }

        graphics.enableScissor(clipX0, clipY0, clipX1, clipY1);
        graphics.blit(IMG_ID, clipX0, clipY0, 0, 0, clipW, clipH, clipW, clipH);
        graphics.disableScissor();

        // Selected block item sprite, standing on the center turf (live).
        // Size follows the scene zoom (no fixed clamps): tiny when zoomed
        // out, large when zoomed in.
        float[] pc = worldToScreen(PEDESTAL_X, FIELD_Y + 1, PEDESTAL_Z);
        float ppx = pc[0];
        float ppy = pc[1];
        ItemStack stack = center == null ? null : LightEditorScreen.safeIcon(center);
        if (stack != null
                && ppx >= clipX0 - 40 && ppx <= clipX1 + 40
                && ppy >= clipY0 - 48 && ppy <= clipY1 + 40) {
            float iconPx = Math.max(2.0F, Math.min(128.0F, viewHw * 2.0F * 1.15F));
            float iconScale = iconPx / 16.0F;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(Math.round(ppx), Math.round(ppy + 1.0F - iconPx / 2.0F), 0.0);
            pose.scale(iconScale, iconScale, 1.0F);
            try {
                graphics.renderItem(stack, -8, -8);
            } catch (Exception ignored) {
                // Item sprites may need state unavailable right after launch;
                // the baked glass diamond underneath stays visible.
            }
            pose.popPose();
        }

        // 2D overlay: level numbers, then the caption.
        Font font = Minecraft.getInstance().font;
        int[] leg = legendRect(x, y, w, h);
        if (showLevels && numCount > 0) {
            float textScale = viewEff >= 28.0F ? 1.0F : 0.5F;
            graphics.enableScissor(clipX0, clipY0, clipX1, clipY1);
            for (int i = 0; i < numCount; i++) {
                float nx = numX[i];
                float ny = numY[i];
                if (nx < clipX0 - 8 || nx > clipX1 + 8 || ny < clipY0 - 8 || ny > clipY1 + 8) {
                    continue;
                }
                if (leg != null && nx >= leg[0] - 2 && nx < leg[0] + leg[2] + 2
                        && ny >= leg[1] - 2 && ny < leg[1] + leg[3] + 2) {
                    continue;
                }
                if (avoid != null && nx >= avoid[0] - 2 && nx < avoid[0] + avoid[2] + 2
                        && ny >= avoid[1] - 2 && ny < avoid[1] + avoid[3] + 2) {
                    continue;
                }
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(nx, ny, 0.0);
                pose.scale(textScale, textScale, 1.0F);
                graphics.drawCenteredString(font, Integer.toString(numV[i]),
                        0, -4, colorForLevel(numV[i]));
                pose.popPose();
            }
            graphics.disableScissor();
        }
        {
            Component name = center == null ? Component.literal("?") : center.getName();
            String rPart = radius == savedRadius
                    ? "R=" + radius : "R=" + radius + " (saved " + savedRadius + ")";
            String caption = showLevels
                    ? name.getString() + " " + rPart + " field " + extent + "x" + extent
                    : name.getString() + " " + rPart;
            int capW = font.width(caption);
            graphics.drawString(font, caption,
                    Math.min(Math.max(innerX, Math.round(viewCx - capW / 2.0F)), innerX + innerW - capW),
                    y + h - PAD - 9, LeTheme.TEXT_DIM);
        }

        // Legend on top of everything scene-related.
        drawLegend(graphics, x, y, w, h);
    }

    private static final int ABGR_GRASS = 0xFF000000 | 0x4AB374;
    private static final int ABGR_STONE_L = 0xFF000000 | 0x7C7676;
    private static final int ABGR_STONE_R = 0xFF000000 | 0x666060;
    private static final int ABGR_GLASS = 0xFF000000 | 0xE8D89F;

    /**
     * Rasterizes the whole field (skirts, tops with merged shade, shrink
     * rings, pedestal glass) into {@link #bakeImg}. Same painter order and
     * math as the per-frame version; early-outs use the clip rect (a superset
     * of the image, so nothing visible is ever skipped).
     */
    private static void bakeField(int radius, double now, int lo, int hi,
                                  int clipX0, int clipY0, int clipX1, int clipY1) {
        float skirtH = 2.5F * viewLh;
        float lipH = 0.6F * viewLh;
        // Rim skirts first (surface diamonds overlap their upper edge).
        // Painter order: far (small gx + gz) first.
        for (int sum = 2 * lo; sum <= 2 * hi; sum++) {
            for (int gx = lo; gx <= hi; gx++) {
                int gz = sum - gx;
                if (gz < lo || gz > hi) {
                    continue;
                }
                boolean rimRight = gx == hi;
                boolean rimLeft = gz == hi;
                if (!rimRight && !rimLeft) {
                    continue;
                }
                float[] p = worldToScreen(gx, FIELD_Y + 1, gz);
                float px = p[0];
                float py = p[1] - growOffset(gx, gz, now) * viewLh;
                if (px + viewHw < clipX0 || px - viewHw > clipX1
                        || py - viewHh > clipY1 || py + viewHh + skirtH < clipY0) {
                    continue;
                }
                if (rimLeft) {
                    plotWall(px - viewHw, py, px, py + viewHh, skirtH, lipH, ABGR_STONE_L);
                }
                if (rimRight) {
                    plotWall(px + viewHw, py, px, py + viewHh, skirtH, lipH, ABGR_STONE_R);
                }
            }
        }
        // Grass tops with the shade baked in, far to near.
        for (int sum = 2 * lo; sum <= 2 * hi; sum++) {
            for (int gx = lo; gx <= hi; gx++) {
                int gz = sum - gx;
                if (gz < lo || gz > hi) {
                    continue;
                }
                float[] p = worldToScreen(gx, FIELD_Y + 1, gz);
                float px = p[0];
                float py = p[1] - growOffset(gx, gz, now) * viewLh;
                if (px + viewHw < clipX0 || px - viewHw > clipX1
                        || py + viewHh < clipY0 || py - viewHh > clipY1) {
                    continue;
                }
                int v = Math.max(0, surfaceLevel(radius, gx, gz));
                plotDiamond(px, py, viewHw, viewHh, v >= 15 ? ABGR_GRASS : shadedTopAbgr(v));
            }
        }
        // Shrinking: dropped rings fall off downward, then vanish.
        if (animEnabled && animToHalf >= 0 && animToHalf < animFromHalf && now < animEnd()) {
            int slo = PEDESTAL_X - animFromHalf;
            int shi = PEDESTAL_X + animFromHalf;
            for (int gx = slo; gx <= shi; gx++) {
                for (int gz = slo; gz <= shi; gz++) {
                    int cheb = chebDist(gx, gz);
                    if (cheb <= animToHalf || cheb > animFromHalf) {
                        continue;
                    }
                    double delay = Math.min((animFromHalf - cheb) * ANIM_STAGGER, ANIM_STAGGER_CAP)
                            + cellJitter(gx, gz) * ANIM_JITTER;
                    double p = (now - animT0 - delay) / ANIM_SHRINK_DUR;
                    if (p >= 1.0) {
                        continue;
                    }
                    // Waiting cells stay in place (never a void!); flying ones fall down.
                    float sink = p <= 0.0 ? 0.0F : (float) (-ANIM_SINK * p * p);
                    float[] sp = worldToScreen(gx, FIELD_Y + 1, gz);
                    float px = sp[0];
                    float py = sp[1] - sink * viewLh;
                    if (px + viewHw < clipX0 || px - viewHw > clipX1
                            || py + viewHh + skirtH < clipY0 || py - viewHh > clipY1) {
                        continue;
                    }
                    if (gx == slo || gx == shi || gz == slo || gz == shi) {
                        if (gz == shi) {
                            plotWall(px - viewHw, py, px, py + viewHh, skirtH, lipH, ABGR_STONE_L);
                        }
                        if (gx == shi) {
                            plotWall(px + viewHw, py, px, py + viewHh, skirtH, lipH, ABGR_STONE_R);
                        }
                    }
                    plotDiamond(px, py, viewHw, viewHh, ABGR_GRASS);
                }
            }
        }
        // Center marker at turf level: small glass diamond under the live item
        // sprite (the sprite covers it whenever one exists).
        float[] pc = worldToScreen(PEDESTAL_X, FIELD_Y + 1, PEDESTAL_Z);
        float ppx = pc[0];
        float ppy = pc[1];
        if (ppx + viewHw >= clipX0 && ppx - viewHw <= clipX1
                && ppy + viewHh >= clipY0 && ppy - viewHh <= clipY1) {
            plotDiamond(ppx, ppy, viewHw * 0.7F, viewHh * 0.7F, ABGR_GLASS);
        }
    }

    /** Rim wall with a grass lip, plotted into the bake image (screen coords). */
    private static void plotWall(double ox, double oy, double sx, double sy,
                                 float wallH, float lipH, int abgrWall) {
        boolean left = ox < sx;
        plotQuad(ox - bakeX, oy - bakeY, sx - bakeX, sy - bakeY,
                sx - bakeX, sy + wallH - bakeY, ox - bakeX, oy + wallH - bakeY, abgrWall);
        plotQuad(ox - bakeX, oy - bakeY, sx - bakeX, sy - bakeY,
                sx - bakeX, sy + lipH - bakeY, ox - bakeX, oy + lipH - bakeY, ABGR_GRASS);
    }

    private static void plotDiamond(double cx, double cy, double hw, double hh, int abgr) {
        int w = bakeImg.getWidth();
        int h = bakeImg.getHeight();
        double lx = cx - bakeX;
        double ly = cy - bakeY;
        int rgba = abgrToRgba(abgr);
        if (hw < 0.5 || hh < 0.5) {
            // Sub-pixel cell at huge radii: single dot keeps the mass solid.
            int x = (int) Math.round(lx);
            int y = (int) Math.round(ly);
            if (x >= 0 && x < w && y >= 0 && y < h) {
                bakeImg.setPixelRGBA(x, y, rgba);
            }
            return;
        }
        int y0 = Math.max(0, (int) Math.floor(ly - hh));
        int y1 = Math.min(h - 1, (int) Math.ceil(ly + hh));
        for (int y = y0; y <= y1; y++) {
            double ny = Math.abs(y + 0.5 - ly) / hh;
            if (ny > 1.0) {
                continue;
            }
            double half = hw * (1.0 - ny);
            int xa = Math.max(0, (int) Math.ceil(lx - half - 0.5));
            int xb = Math.min(w - 1, (int) Math.floor(lx + half - 0.5));
            for (int x = xa; x <= xb; x++) {
                bakeImg.setPixelRGBA(x, y, rgba);
            }
        }
    }

    /** Scanline-filled convex quad plotted into the bake image (image coords). */
    private static void plotQuad(double x0, double y0, double x1, double y1,
                                 double x2, double y2, double x3, double y3, int abgr) {
        int w = bakeImg.getWidth();
        int h = bakeImg.getHeight();
        int rgba = abgrToRgba(abgr);
        double[] xs = {x0, x1, x2, x3};
        double[] ys = {y0, y1, y2, y3};
        double minY = Math.min(Math.min(y0, y1), Math.min(y2, y3));
        double maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));
        int ya = Math.max(0, (int) Math.floor(minY));
        int yb = Math.min(h - 1, (int) Math.ceil(maxY));
        for (int y = ya; y <= yb; y++) {
            double scan = y + 0.5;
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            int n = 0;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3;
                double yA = ys[i];
                double yB = ys[j];
                if (scan >= Math.min(yA, yB) && scan < Math.max(yA, yB)) {
                    double t = (scan - yA) / (yB - yA);
                    double hx = xs[i] + t * (xs[j] - xs[i]);
                    if (hx < lo) {
                        lo = hx;
                    }
                    if (hx > hi) {
                        hi = hx;
                    }
                    n++;
                }
            }
            if (n < 2) {
                continue;
            }
            int xa = Math.max(0, (int) Math.ceil(lo - 0.5));
            int xb = Math.min(w - 1, (int) Math.floor(hi - 0.5));
            for (int x = xa; x <= xb; x++) {
                bakeImg.setPixelRGBA(x, y, rgba);
            }
        }
    }

    /** Hint plaque rect {x, y, w, h}, or null when hidden (too small). */
    private static int[] legendRect(int x, int y, int w, int h) {
        if (w < 170 || h < 150) {
            return null;
        }
        int boxW = 4 + 24 + 3 + 8 + 3 + 12 + 4;
        int boxH = 4 + 12 + 3 + 12 + 4;
        return new int[]{x + w - PAD - boxW, y + PAD, boxW, boxH};
    }

    /** Ctrl-hint legend: keycap + wheel icon (zoom), keycap + move icon (pan). */
    private static void drawLegend(GuiGraphics graphics, int x, int y, int w, int h) {
        int[] box = legendRect(x, y, w, h);
        if (box == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int keyW = 24;
        int rowH = 12;
        int gap = 3;
        int pad = 4;
        int bx = box[0];
        int by = box[1];
        int boxW = box[2];
        int boxH = box[3];
        LeTheme.frame(graphics, bx, by, boxW, boxH, 0xC0101014, LeTheme.PANEL_BORDER);

        int row1 = by + pad;
        int row2 = row1 + rowH + gap;
        drawKeycap(graphics, font, bx + pad, row1, keyW, rowH, "ctrl");
        graphics.drawString(font, "+", bx + pad + keyW + gap, row1 + 2, LeTheme.TEXT_DIM);
        drawWheelIcon(graphics, bx + pad + keyW + gap + 8 + gap, row1, 12);

        drawKeycap(graphics, font, bx + pad, row2, keyW, rowH, "ctrl");
        graphics.drawString(font, "+", bx + pad + keyW + gap, row2 + 2, LeTheme.TEXT_DIM);
        drawDragIcon(graphics, bx + pad + keyW + gap + 8 + gap, row2, 12);
    }

    private static void drawKeycap(GuiGraphics graphics, Font font, int px, int py, int kw, int kh, String text) {
        graphics.fill(px, py, px + kw, py + kh, LeTheme.BTN_BG);
        graphics.fill(px, py, px + kw, py + 1, LeTheme.BTN_BORDER);
        graphics.fill(px, py + kh - 1, px + kw, py + kh, LeTheme.BTN_BORDER);
        graphics.fill(px, py, px + 1, py + kh, LeTheme.BTN_BORDER);
        graphics.fill(px + kw - 1, py, px + kw, py + kh, LeTheme.BTN_BORDER);
        graphics.drawCenteredString(font, text, px + kw / 2, py + 2, LeTheme.TEXT);
    }

    /** Mouse glyph: body outline with a wheel. */
    private static void drawWheelIcon(GuiGraphics graphics, int px, int py, int s) {
        int c = LeTheme.TEXT_DIM;
        graphics.fill(px + 2, py, px + s - 2, py + 1, c);
        graphics.fill(px + 2, py + s - 1, px + s - 2, py + s, c);
        graphics.fill(px + 1, py + 2, px + 2, py + s - 2, c);
        graphics.fill(px + s - 2, py + 2, px + s - 1, py + s - 2, c);
        graphics.fill(px + 1, py + 1, px + 2, py + 2, c);
        graphics.fill(px + s - 2, py + 1, px + s - 1, py + 2, c);
        graphics.fill(px + 1, py + s - 2, px + 2, py + s - 1, c);
        graphics.fill(px + s - 2, py + s - 2, px + s - 1, py + s - 1, c);
        graphics.fill(px + s / 2 - 1, py + 2, px + s / 2 + 1, py + 6, LeTheme.ACCENT);
    }

    /**
     * Drag glyph: mouse with the highlighted (held) left button,
     * four arrows around for pan in any direction.
     */
    private static void drawDragIcon(GuiGraphics graphics, int px, int py, int s) {
        int c = LeTheme.TEXT_DIM;
        int a = LeTheme.ACCENT;
        // Up arrow.
        graphics.fill(px + 5, py, px + 7, py + 1, c);
        graphics.fill(px + 4, py + 1, px + 8, py + 2, c);
        graphics.fill(px + 5, py + 2, px + 7, py + 4, c);
        // Mouse body.
        graphics.fill(px + 4, py + 4, px + 8, py + 5, c);
        graphics.fill(px + 4, py + 8, px + 8, py + 9, c);
        graphics.fill(px + 4, py + 5, px + 5, py + 8, c);
        graphics.fill(px + 7, py + 5, px + 8, py + 8, c);
        // Held left button.
        graphics.fill(px + 4, py + 5, px + 6, py + 7, a);
        // Down arrow.
        graphics.fill(px + 5, py + 9, px + 7, py + 10, c);
        graphics.fill(px + 4, py + 10, px + 8, py + 11, c);
        graphics.fill(px + 5, py + 11, px + 7, py + 12, c);
        // Left arrow.
        graphics.fill(px, py + 5, px + 1, py + 7, c);
        graphics.fill(px + 1, py + 4, px + 2, py + 8, c);
        graphics.fill(px + 2, py + 5, px + 4, py + 7, c);
        // Right arrow.
        graphics.fill(px + 8, py + 5, px + 10, py + 7, c);
        graphics.fill(px + 10, py + 4, px + 11, py + 8, c);
        graphics.fill(px + 11, py + 5, px + 12, py + 7, c);
    }
}
