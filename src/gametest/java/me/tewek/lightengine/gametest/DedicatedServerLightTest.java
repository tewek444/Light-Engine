package me.tewek.lightengine.gametest;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightEngineApi;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.client.EditorSync;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Multiplayer check on a real dedicated server (in-process, real network
 * connection): extended light reaches the client, light removal syncs,
 * rejoining re-sends it, and only the owner (op level 4) may change
 * profiles. Results are logged as "[server-test]" lines.
 */
public class DedicatedServerLightTest implements FabricClientGameTest {
    private static final BlockPos[] PROBES = {
            new BlockPos(0, -60, -3), new BlockPos(0, -60, -6), new BlockPos(4, -60, 0), new BlockPos(-8, -60, 0)};

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
        Properties props = new Properties();
        props.setProperty("level-type", "minecraft:flat");
        props.setProperty("spawn-protection", "0");
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(props)) {
            server.runCommand("time set midnight");
            server.runCommand("fill -12 -61 -12 12 -61 12 minecraft:stone");
            server.runCommand("fill -12 -60 -12 12 -55 12 minecraft:air");
            server.runCommand("setblock 0 -60 0 minecraft:soul_lantern");
            try (var conn = server.connect()) {
                context.waitTicks(100);
                server.runCommand("gamemode spectator @a");
                server.runCommand("tp @a 0.5 -56 -9.5 0 30");
                context.waitTicks(60);
                context.takeScreenshot("srv_lantern_on");
                compare(context, server, "join");

                // Light removal must reach the remote client.
                for (int i = 0; i < 3; i++) {
                    server.runCommand("setblock 0 -60 0 minecraft:stone");
                    server.runCommand("setblock 0 -59 0 minecraft:soul_lantern");
                    context.waitTicks(30);
                    server.runCommand("setblock 0 -60 0 minecraft:air");
                    context.waitTicks(40);
                }
                context.takeScreenshot("srv_lantern_removed");
                compare(context, server, "removed");

                // Permissions: plain player, op level 2, op level 4.
                Identifier torch = Identifier.withDefaultNamespace("torch");
                tryEdit(context, torch, 7, "player");
                server.runOnServer(srv -> srv.getPlayerList().op(srv.getPlayerList().getPlayers().get(0).nameAndId(),
                        Optional.of(LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS)), Optional.empty()));
                context.waitTicks(10);
                tryEdit(context, torch, 8, "op2");
                server.runOnServer(srv -> srv.getPlayerList().op(srv.getPlayerList().getPlayers().get(0).nameAndId(),
                        Optional.of(LevelBasedPermissionSet.forLevel(PermissionLevel.OWNERS)), Optional.empty()));
                context.waitTicks(10);
                tryEdit(context, torch, 9, "op4");

                server.runCommand("setblock 0 -60 0 minecraft:soul_lantern");
                context.waitTicks(60);
            }
            // Rejoin: the field is sent again with the chunks.
            try (var conn = server.connect()) {
                context.waitTicks(100);
                server.runCommand("gamemode spectator @a");
                server.runCommand("tp @a 0.5 -56 -9.5 0 30");
                context.waitTicks(60);
                context.takeScreenshot("srv_rejoin");
                compare(context, server, "rejoin");
            }
        }
    }

    /** Sends an editor change for {@code id} and logs the radius the server ends with. */
    private static void tryEdit(ClientGameTestContext context, Identifier id, int radius, String who) {
        context.runOnClient(mc -> EditorSync.pushDirty(Map.of(id,
                new LightProfileRegistry.Profile(radius, false, 0xFF0000, true))));
        context.waitTicks(20);
        LightEngine.LOGGER.info("[server-test] edit as {} -> sent radius {}", who, radius);
    }

    /** Extended level at the probes, server (server thread) vs client (client thread). */
    private static void compare(ClientGameTestContext context, TestDedicatedServerContext server, String tag) {
        int[] s = server.computeOnServer(srv -> {
            int[] out = new int[PROBES.length];
            for (int i = 0; i < PROBES.length; i++) {
                out[i] = level(srv.overworld(), PROBES[i]);
            }
            return out;
        });
        int[] c = context.computeOnClient(mc -> {
            int[] out = new int[PROBES.length];
            for (int i = 0; i < PROBES.length; i++) {
                out[i] = level(mc.level, PROBES[i]);
            }
            return out;
        });
        StringBuilder sb = new StringBuilder();
        boolean same = true;
        for (int i = 0; i < PROBES.length; i++) {
            same &= s[i] == c[i];
            sb.append(String.format("%s s=%d c=%d  ", PROBES[i].toShortString(), s[i], c[i]));
        }
        LightEngine.LOGGER.info("[server-test] {} {} {}", tag, same ? "MATCH" : "MISMATCH", sb);
    }

    private static int level(Level level, BlockPos pos) {
        return LightEngineApi.getLightLevel(level, pos);
    }
}
