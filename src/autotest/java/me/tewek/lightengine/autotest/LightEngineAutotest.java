package me.tewek.lightengine.autotest;

import java.util.ArrayList;
import java.util.List;
import me.tewek.lightengine.client.gui.LightEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev-only visual smoke test for the NeoForge port (no client gametest API
 * here). Launched with {@code --quickPlaySingleplayer}; once the world is in,
 * it runs a fixed script of server commands, waits and screenshots, then
 * quits. Mirrors the Fabric {@code ColoredLightClientTest}.
 */
@Mod(value = "lightengine_autotest", dist = Dist.CLIENT)
public class LightEngineAutotest {
    private record Step(int waitTicks, Runnable action) {
    }

    private final List<Step> steps = new ArrayList<>();
    private int index;
    private int wait = -1;

    public LightEngineAutotest() {
        cmd(0, "time set midnight");
        cmd(0, "fill -12 -61 -12 12 -55 12 minecraft:air");
        cmd(0, "fill -12 -61 -12 12 -61 12 minecraft:stone");
        cmd(0, "fill -4 -60 3 4 -57 3 minecraft:stone");
        cmd(0, "fill 4 -61 -4 8 -61 0 minecraft:water");
        cmd(0, "setblock 0 -60 0 minecraft:soul_lantern");
        cmd(0, "tp @p 0.5 -56 -9.5 0 30");
        shot(60, "le_lantern_on");
        this.steps.add(new Step(0, () -> {
            MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
            srv.execute(() -> srv.getPlayerList().getPlayers().forEach(p -> srv.getPlayerList().op(p.nameAndId())));
        }));
        this.steps.add(new Step(5, () -> Minecraft.getInstance().player.connection.sendCommand("le light_level")));
        shot(10, "le_cmd_overlay");
        this.steps.add(new Step(0, () -> Minecraft.getInstance().player.connection.sendCommand("light_engine light_level")));
        this.steps.add(new Step(0, () -> Minecraft.getInstance().player.connection.sendCommand("le dump 0 -60 0")));
        cmd(0, "setblock -6 -60 -2 minecraft:torch");
        shot(40, "le_lantern_and_torch");
        cmd(0, "setblock 0 -60 0 minecraft:air");
        cmd(0, "setblock -6 -60 -2 minecraft:air");
        cmd(0, "setblock 0 -60 0 minecraft:torch");
        shot(40, "le_torch");
        cmd(0, "setblock 0 -60 0 minecraft:air");
        for (int i = 0; i < 4; i++) {
            cmd(0, "setblock 0 -60 0 minecraft:stone");
            cmd(0, "setblock 0 -59 0 minecraft:soul_lantern");
            cmd(30, "setblock 0 -60 0 minecraft:air");
        }
        shot(40, "le_lantern_removed");
        this.steps.add(new Step(0, () -> Minecraft.getInstance().gui.setScreen(new PauseScreen(true))));
        shot(10, "le_pause");
        this.steps.add(new Step(0, () -> Minecraft.getInstance().gui.setScreen(new LightEditorScreen(null))));
        shot(40, "le_editor");
        this.steps.add(new Step(5, () -> Minecraft.getInstance().stop()));
        NeoForge.EVENT_BUS.register(this);
    }

    private void cmd(int waitBefore, String command) {
        this.steps.add(new Step(waitBefore, () -> {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
            }
        }));
    }

    private void shot(int waitBefore, String name) {
        this.steps.add(new Step(waitBefore, () -> {
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
        }));
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || this.index >= this.steps.size()) {
            return;
        }
        if (this.wait < 0) {
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            this.wait = 40;
        }
        if (this.wait > 0) {
            this.wait--;
            return;
        }
        Step step = this.steps.get(this.index++);
        step.action().run();
        if (this.index < this.steps.size()) {
            this.wait = this.steps.get(this.index).waitTicks();
        }
    }
}
