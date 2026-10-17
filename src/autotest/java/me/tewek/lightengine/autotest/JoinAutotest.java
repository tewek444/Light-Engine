package me.tewek.lightengine.autotest;

import java.util.Map;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightEngineApi;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.client.EditorSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client half of the multiplayer autotest ({@code runAutotestJoin}, joins
 * localhost). Same timer as {@link ServerAutotest}, counted from the first
 * tick in the world: "[cli]" light probes, editor changes as plain player,
 * op 2 and op 4, and the torch radius this client ends up with (a denied edit
 * must be rolled back by the server's resync).
 */
public class JoinAutotest {
    private static final Identifier TORCH = Identifier.withDefaultNamespace("torch");
    private int tick = -1;

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (this.tick < 0) {
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            this.tick = 0;
        }
        int t = this.tick++;
        switch (t) {
            case 200 -> {
                probes(mc, "join");
                shot(mc, "mp_join.png");
            }
            case 400 -> {
                probes(mc, "removed");
                shot(mc, "mp_removed.png");
            }
            case 450 -> edit(7, "player");
            case 480 -> torch("player");
            case 550 -> edit(8, "op2");
            case 580 -> torch("op2");
            case 650 -> edit(9, "op4");
            case 680 -> torch("op4");
            case 800 -> {
                probes(mc, "final");
                shot(mc, "mp_final.png");
            }
            case 860 -> mc.stop();
            default -> {
            }
        }
    }

    private static void edit(int radius, String who) {
        EditorSync.pushDirty(Map.of(TORCH, new LightProfileRegistry.Profile(radius, false, 0xFF0000, true)));
        LightEngine.LOGGER.info("[cli] edit as {} -> radius {}", who, radius);
    }

    private static void torch(String who) {
        var p = LightProfileRegistry.snapshot().get(TORCH);
        LightEngine.LOGGER.info("[cli] torch radius after {} = {}", who, p == null ? -1 : p.lightRadius());
    }

    private static void probes(Minecraft mc, String tag) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : ServerAutotest.PROBES) {
            sb.append(p.toShortString()).append('=').append(LightEngineApi.getLightLevel(mc.level, p)).append("  ");
        }
        LightEngine.LOGGER.info("[cli] {} {}", tag, sb);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
    }
}
