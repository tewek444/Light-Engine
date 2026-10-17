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
        context.runOnClient(mc -> listEmitters());
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
            // Commands typed by the player: client subcommand runs locally,
            // server subcommand under the same /le root must reach the server.
            server.runOnServer(srv -> srv.getPlayerList().getPlayers().forEach(p -> srv.getPlayerList().op(p.nameAndId())));
            context.waitTicks(5);
            context.runOnClient(mc -> mc.player.connection.sendCommand("le light_level"));
            context.waitTicks(10);
            context.takeScreenshot("le_cmd_overlay");
            context.runOnClient(mc -> mc.player.connection.sendCommand("light_engine light_level"));
            context.runOnClient(mc -> mc.player.connection.sendCommand("le dump 0 -60 0"));
            context.waitTicks(10);
            context.runOnClient(mc -> diagnose(mc));
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
            server.runCommand("setblock 0 -60 0 minecraft:copper_lantern");
            server.runCommand("setblock -4 -60 -3 minecraft:lime_candle[candles=4,lit=true]");
            server.runCommand("setblock 4 -60 -3 minecraft:magenta_candle[candles=1,lit=true]");
            context.waitTicks(40);
            context.takeScreenshot("le_copper_and_candles");
            server.runCommand("setblock -4 -60 -3 minecraft:air");
            server.runCommand("setblock 4 -60 -3 minecraft:air");

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
            // Left click on a tile of the block list: selects it (26.3 SDL button numbering).
            context.getInput().setCursorPos(578, 180);
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(10);
            context.takeScreenshot("le_editor_left_click");
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(10);
            context.takeScreenshot("le_editor_right_click");
            context.setScreen(() -> null);
        }
    }

    /** Logs whether the render hooks are woven and what the tint solve returns near the lantern. */
    private static void diagnose(net.minecraft.client.Minecraft mc) {
        org.slf4j.Logger log = me.tewek.lightengine.LightEngine.LOGGER;
        for (String cls : new String[]{
                "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl",
                "net.minecraft.client.renderer.block.ModelBlockRenderer",
                "net.minecraft.client.renderer.block.FluidRenderer"}) {
            try {
                StringBuilder sb = new StringBuilder();
                for (java.lang.reflect.Method m : Class.forName(cls).getDeclaredMethods()) {
                    if (m.getName().contains("lightengine")) {
                        sb.append(m.getName()).append(' ');
                    }
                }
                log.info("[diag] {} woven: {}", cls, sb);
            } catch (Throwable t) {
                log.info("[diag] {} lookup failed: {}", cls, t.toString());
            }
        }
        net.minecraft.core.BlockPos floor = new net.minecraft.core.BlockPos(0, -60, -3);
        var level = mc.level;
        int block = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, floor);
        int tint = me.tewek.lightengine.lightfield.ChromaTint.forSurface(level, floor, level.getBlockState(floor));
        float[] x = {0, 1, 1, 0};
        float[] y = {0, 0, 0, 0};
        float[] z = {0, 0, 1, 1};
        int packed = net.minecraft.util.LightCoordsUtil.pack(Math.min(block, 15), 0);
        int[] light = {packed, packed, packed, packed};
        var res = me.tewek.lightengine.client.QuadTinter.compute(level, floor.below(),
                net.minecraft.core.Direction.UP, x, new float[]{1, 1, 1, 1}, z, light, 4);
        log.info("[diag] floor block={} tint={} quadTinter={} mult0={},{},{}", block,
                Integer.toHexString(tint), res != null,
                res == null ? -1 : res.mult[0], res == null ? -1 : res.mult[1], res == null ? -1 : res.mult[2]);
    }

    /** Every block that can emit light, with its max emission and preset state. */
    private static void listEmitters() {
        var presets = me.tewek.lightengine.LightPresets.build(me.tewek.lightengine.LightPresets.Mode.RADIUS_AND_COLOR);
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            int max = 0;
            for (var st : block.getStateDefinition().getPossibleStates()) {
                max = Math.max(max, st.getLightEmission());
            }
            if (max > 0) {
                var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
                me.tewek.lightengine.LightEngine.LOGGER.info("[emit] {} {} {}", id, max, presets.containsKey(id) ? "preset" : "MISSING");
            }
        }
    }
}
