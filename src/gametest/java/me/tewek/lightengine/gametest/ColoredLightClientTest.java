package me.tewek.lightengine.gametest;

import me.tewek.lightengine.client.gui.LightEditorScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.sounds.SoundSource;

/**
 * Visual smoke test: colored light on terrain and water, light removal after
 * the support breaks, the pause-menu button and the editor. Writes
 * screenshots to the run dir; nothing asserts on pixels.
 */
public class ColoredLightClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        });
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set midnight");
            server.runCommand("fill -12 -61 -12 12 -55 12 minecraft:air");
            server.runCommand("fill -12 -61 -12 12 -61 12 minecraft:stone");
            server.runCommand("fill -4 -60 3 4 -57 3 minecraft:stone");
            server.runCommand("fill 4 -61 -4 8 -61 0 minecraft:water");
            server.runCommand("setblock 0 -60 0 minecraft:soul_lantern");
            server.runCommand("tp @p 0.5 -56 -9.5 0 30");
            context.waitTicks(60);
            context.takeScreenshot("le_lantern_on");
            server.runCommand("tp @p 0.5 -58 -3.5 0 50");
            context.waitTicks(20);
            context.takeScreenshot("le_lantern_close");
            context.runOnClient(ColoredLightClientTest::diagnose);
            server.runCommand("tp @p 0.5 -56 -9.5 0 30");
            server.runCommand("setblock -6 -60 -2 minecraft:torch");
            context.waitTicks(40);
            context.takeScreenshot("le_lantern_and_torch");
            server.runCommand("setblock 0 -60 0 minecraft:air");
            server.runCommand("setblock -6 -60 -2 minecraft:air");
            server.runCommand("setblock 0 -60 0 minecraft:torch");
            context.waitTicks(40);
            context.takeScreenshot("le_torch");
            server.runCommand("setblock 0 -60 0 minecraft:sea_lantern");
            context.waitTicks(40);
            context.takeScreenshot("le_sea_lantern");
            server.runCommand("setblock 0 -60 0 minecraft:redstone_torch");
            context.waitTicks(40);
            context.takeScreenshot("le_redstone_torch");

            // Bug #1: lantern on a support; breaking the support must drop the light.
            server.runCommand("setblock 0 -60 0 minecraft:air");
            server.runCommand("setblock -6 -60 -2 minecraft:air");
            for (int i = 0; i < 4; i++) {
                server.runCommand("setblock 0 -60 0 minecraft:stone");
                server.runCommand("setblock 0 -59 0 minecraft:soul_lantern");
                context.waitTicks(30);
                server.runCommand("setblock 0 -60 0 minecraft:air");
                context.waitTicks(40);
            }
            context.takeScreenshot("le_lantern_removed");

            context.setScreen(() -> new PauseScreen(true));
            context.waitTicks(10);
            context.takeScreenshot("le_pause");
            context.setScreen(() -> new LightEditorScreen(null));
            context.waitTicks(40);
            context.takeScreenshot("le_editor");
            context.setScreen(() -> null);
        }
    }

    /** Light values the renderers see on the floor next to the lantern. */
    private static void diagnose(net.minecraft.client.Minecraft mc) {
        var level = mc.level;
        for (int d = 0; d <= 6; d++) {
            net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(0, -60, -d);
            int nib = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p);
            int def = net.minecraft.client.renderer.LevelRenderer.BrightnessGetter.DEFAULT.packedBrightness(level, p);
            int col = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, p);
            int ext = me.tewek.lightengine.LightEngineApi.getLightLevel(level, p);
            me.tewek.lightengine.LightEngine.LOGGER.info("[diag] d={} nibble={} defaultPacked={} lightColor={} extended={}",
                    d, nib, Integer.toHexString(def), Integer.toHexString(col), ext);
        }
    }
}
