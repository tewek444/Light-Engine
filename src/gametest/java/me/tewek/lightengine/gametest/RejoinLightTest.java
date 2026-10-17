package me.tewek.lightengine.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.sounds.SoundSource;

/**
 * Extended light must survive leaving and rejoining the world: halos that
 * cross chunk borders were cut along those borders after a reload.
 * Screenshots from above before and after the rejoin, plus light dumps.
 */
public class RejoinLightTest implements FabricClientGameTest {
    private static final int[][] PROBES = {{8, 0}, {16, 0}, {20, 0}, {24, 0}, {28, 0}, {8, 12}, {8, 20}, {-4, 0}};

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
        TestWorldSave save;
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            save = sp.getWorldSave();
            TestServerContext server = sp.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("time set midnight");
            // lanterns next to chunk borders (chunks are 16 wide: x=15|16, z=15|16)
            server.runCommand("setblock 8 -60 8 minecraft:soul_lantern");
            server.runCommand("setblock 30 -60 8 minecraft:lantern");
            server.runCommand("setblock 8 -60 30 minecraft:redstone_torch");
            server.runCommand("tp @p 16 -20 16 0 90");
            context.waitTicks(80);
            context.takeScreenshot("rejoin_before");
            context.runOnClient(mc -> dump(mc, "before"));
            server.runOnServer(srv -> dumpLevel(srv.overworld(), "server before"));
        }
        try (TestSingleplayerContext sp = save.open()) {
            sp.getServer().runCommand("gamemode spectator @a");
            sp.getServer().runCommand("tp @p 16 -20 16 0 90");
            context.waitTicks(80);
            context.takeScreenshot("rejoin_after");
            context.runOnClient(mc -> dump(mc, "after"));
            sp.getServer().runOnServer(srv -> dumpLevel(srv.overworld(), "server after"));
        }
    }

    private static void dump(net.minecraft.client.Minecraft mc, String tag) {
        dumpLevel(mc.level, tag);
    }

    private static void dumpLevel(net.minecraft.world.level.Level level, String tag) {
        StringBuilder sb = new StringBuilder();
        for (int[] p : PROBES) {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(p[0], -60, p[1] + 8);
            sb.append(String.format("(%d,%d) n=%d e=%d  ", pos.getX(), pos.getZ(),
                    level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos),
                    me.tewek.lightengine.LightEngineApi.getLightLevel(level, pos)));
        }
        me.tewek.lightengine.LightEngine.LOGGER.info("[rejoin {}] {}", tag, sb);
    }
}
